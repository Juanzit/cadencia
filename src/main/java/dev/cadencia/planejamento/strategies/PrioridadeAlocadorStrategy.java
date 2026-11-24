package dev.cadencia.planejamento.strategies;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.planejamento.PedacoAlocado;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Estratégia determinística de alocação baseada em prioridade e balanceamento diário: ordena as
 * tarefas pelo menor número de {@link Tarefa#prioridade()} primeiro, preservando em caso de empate
 * a ordem declarada no roadmap, e para cada {@link Pedaco} seleciona entre os {@link Bloco}s
 * compatíveis aquele cuja data possui a menor carga já alocada na semana (em nanossegundos),
 * desempatando cronologicamente por {@code (data, inicio, fim, tipo)}.
 *
 * <p>O filtro individual por {@link Tarefa#tiposAceitos()} é aplicado diretamente na seleção do
 * bloco para cada pedaço; caso haja empate total em {@code (data, inicio, fim, tipo)}, a ordem
 * original dos blocos recebidos é preservada por ordenação estável.
 */
public class PrioridadeAlocadorStrategy implements AlocadorStrategy {

    private static final Comparator<Bloco> ORDEM_CRONOLOGICA_BLOCOS = Comparator
            .comparing(Bloco::data)
            .thenComparing(Bloco::inicio)
            .thenComparing(Bloco::fim)
            .thenComparing(Bloco::tipo);

    private static final Comparator<PedacoAlocado> ORDEM_CRONOLOGICA_ALOCADOS = Comparator
            .comparing(PedacoAlocado::data)
            .thenComparing(PedacoAlocado::inicio)
            .thenComparing(PedacoAlocado::fim);

    @Override
    public ResultadoAlocacao alocar(List<Bloco> blocosDisponiveis, List<Tarefa> tarefas) {
        return alocarComCompatibilidade(
                blocosDisponiveis,
                tarefas,
                (bloco, tarefa) -> tarefa.tiposAceitos().contains(bloco.tipo()));
    }

    static ResultadoAlocacao alocarComCompatibilidade(
            List<Bloco> blocosDisponiveis,
            List<Tarefa> tarefas,
            BiPredicate<Bloco, Tarefa> regraCompatibilidade) {
        Objects.requireNonNull(blocosDisponiveis, "blocosDisponiveis não pode ser nulo");
        Objects.requireNonNull(tarefas, "tarefas não pode ser nulo");
        Objects.requireNonNull(regraCompatibilidade, "regraCompatibilidade não pode ser nula");

        List<Bloco> blocosCopia = List.copyOf(blocosDisponiveis);
        List<Tarefa> tarefasCopia = List.copyOf(tarefas);

        // Stream.sorted é estável para listas: blocos iguais em (data, inicio, fim, tipo) mantêm a ordem original.
        List<SlotBloco> slots = blocosCopia.stream()
                .sorted(ORDEM_CRONOLOGICA_BLOCOS)
                .map(SlotBloco::new)
                .toList();

        // Stream.sorted é estável para listas ordenadas, mantendo a ordem do roadmap no empate.
        List<Tarefa> tarefasPriorizadas = tarefasCopia.stream()
                .sorted(Comparator.comparingInt(Tarefa::prioridade))
                .toList();

        Map<LocalDate, Long> cargaPorDiaNanos = new HashMap<>();
        List<PedacoAlocado> alocados = new ArrayList<>();
        List<Pedaco> naoAlocados = new ArrayList<>();

        for (Tarefa tarefa : tarefasPriorizadas) {
            for (Pedaco pedaco : tarefa.dividirEmPedacos()) {
                SlotBloco slot = encontrarSlotMenorCargaDiaria(
                        slots, tarefa, pedaco, regraCompatibilidade, cargaPorDiaNanos);
                if (slot != null) {
                    PedacoAlocado alocado = slot.alocar(pedaco);
                    alocados.add(alocado);
                    cargaPorDiaNanos.merge(alocado.data(), pedaco.duracao().toNanos(), Long::sum);
                } else {
                    naoAlocados.add(pedaco);
                }
            }
        }

        alocados.sort(ORDEM_CRONOLOGICA_ALOCADOS);
        return new ResultadoAlocacao(alocados, naoAlocados);
    }

    private static SlotBloco encontrarSlotMenorCargaDiaria(
            List<SlotBloco> slots,
            Tarefa tarefa,
            Pedaco pedaco,
            BiPredicate<Bloco, Tarefa> regraCompatibilidade,
            Map<LocalDate, Long> cargaPorDiaNanos) {
        SlotBloco melhorSlot = null;
        long menorCargaNanos = Long.MAX_VALUE;
        for (SlotBloco slot : slots) {
            if (!slot.aceita(tarefa, pedaco, regraCompatibilidade)) {
                continue;
            }
            long cargaDiaNanos = cargaPorDiaNanos.getOrDefault(slot.data(), 0L);
            if (melhorSlot == null || cargaDiaNanos < menorCargaNanos) {
                melhorSlot = slot;
                menorCargaNanos = cargaDiaNanos;
            }
        }
        return melhorSlot;
    }

    private static final class SlotBloco {

        private final Bloco bloco;
        private LocalTime proximoInicio;

        private SlotBloco(Bloco bloco) {
            this.bloco = bloco;
            this.proximoInicio = bloco.inicio();
        }

        private LocalDate data() {
            return bloco.data();
        }

        private boolean aceita(
                Tarefa tarefa, Pedaco pedaco, BiPredicate<Bloco, Tarefa> regraCompatibilidade) {
            if (!regraCompatibilidade.test(bloco, tarefa)) {
                return false;
            }
            // Compara em nanossegundos para nunca ocorrer wrap-around de LocalTime perto de 00:00.
            long restanteNanos = Duration.between(proximoInicio, bloco.fim()).toNanos();
            return restanteNanos >= pedaco.duracao().toNanos();
        }

        private PedacoAlocado alocar(Pedaco pedaco) {
            LocalTime inicioAlocado = proximoInicio;
            LocalTime fimAlocado = inicioAlocado.plusNanos(pedaco.duracao().toNanos());
            this.proximoInicio = fimAlocado;
            return new PedacoAlocado(bloco, bloco.data(), inicioAlocado, fimAlocado, pedaco);
        }
    }
}
