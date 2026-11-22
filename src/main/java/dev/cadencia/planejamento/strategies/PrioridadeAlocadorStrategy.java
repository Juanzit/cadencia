package dev.cadencia.planejamento.strategies;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.planejamento.PedacoAlocado;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Estratégia determinística de alocação baseada em prioridade: ordena as tarefas pelo menor
 * número de {@link Tarefa#prioridade()} primeiro, preservando em caso de empate a ordem declarada
 * no roadmap, e aloca cada {@link Pedaco} no primeiro {@link Bloco} cronologicamente disponível e
 * compatível.
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
        Objects.requireNonNull(blocosDisponiveis, "blocosDisponiveis não pode ser nulo");
        Objects.requireNonNull(tarefas, "tarefas não pode ser nulo");

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

        List<PedacoAlocado> alocados = new ArrayList<>();
        List<Pedaco> naoAlocados = new ArrayList<>();

        for (Tarefa tarefa : tarefasPriorizadas) {
            for (Pedaco pedaco : tarefa.dividirEmPedacos()) {
                SlotBloco slot = encontrarPrimeiroSlotCompativel(slots, tarefa, pedaco);
                if (slot != null) {
                    alocados.add(slot.alocar(pedaco));
                } else {
                    naoAlocados.add(pedaco);
                }
            }
        }

        alocados.sort(ORDEM_CRONOLOGICA_ALOCADOS);
        return new ResultadoAlocacao(alocados, naoAlocados);
    }

    private static SlotBloco encontrarPrimeiroSlotCompativel(
            List<SlotBloco> slots, Tarefa tarefa, Pedaco pedaco) {
        for (SlotBloco slot : slots) {
            if (slot.aceita(tarefa, pedaco)) {
                return slot;
            }
        }
        return null;
    }

    private static final class SlotBloco {

        private final Bloco bloco;
        private LocalTime proximoInicio;

        private SlotBloco(Bloco bloco) {
            this.bloco = bloco;
            this.proximoInicio = bloco.inicio();
        }

        private boolean aceita(Tarefa tarefa, Pedaco pedaco) {
            if (!tarefa.tiposAceitos().contains(bloco.tipo())) {
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
