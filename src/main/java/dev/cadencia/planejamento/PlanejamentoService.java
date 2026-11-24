package dev.cadencia.planejamento;

import dev.cadencia.agenda.IntervaloOcupado;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.strategies.AlocadorStrategy;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por aplicar a folga mínima configurável nos blocos livres
 * exatamente adjacentes a ocupações no mesmo dia, delegar a distribuição em chamada única à
 * {@link AlocadorStrategy} sem reiniciar capacidades e garantir a compatibilidade individual entre
 * cada {@link Pedaco} e o {@link Bloco} de destino.
 */
public class PlanejamentoService {

    public static final Duration FOLGA_MINIMA_PADRAO = Duration.ofMinutes(10);
    private static final long NANOS_POR_DIA = Duration.ofDays(1).toNanos();

    private final AlocadorStrategy alocadorStrategy;
    private final Duration folgaMinima;

    public PlanejamentoService(AlocadorStrategy alocadorStrategy) {
        this(alocadorStrategy, FOLGA_MINIMA_PADRAO);
    }

    public PlanejamentoService(AlocadorStrategy alocadorStrategy, Duration folgaMinima) {
        this.alocadorStrategy = Objects.requireNonNull(
                alocadorStrategy, "alocadorStrategy não pode ser nula");
        this.folgaMinima = Objects.requireNonNull(folgaMinima, "folgaMinima não pode ser nula");
        if (folgaMinima.isNegative()) {
            throw new IllegalArgumentException("folgaMinima não pode ser negativa");
        }
    }

    /**
     * Aplica a folga mínima configurada nos blocos da {@code semanaPlanejada} com ocupações
     * adjacentes no mesmo dia e aloca as {@code tarefas} em chamada única para a estratégia,
     * garantindo compatibilidade individual por {@link Tarefa#tiposAceitos()}.
     */
    public ResultadoAlocacao alocar(SemanaPlanejada semanaPlanejada, List<Tarefa> tarefas) {
        return alocarInterno(semanaPlanejada, tarefas, null);
    }

    /**
     * Aplica a folga mínima configurada nos blocos da {@code semanaPlanejada} e aloca as
     * {@code tarefas} considerando o índice histórico de confiabilidade por {@link FaixaHorario},
     * mantendo a compatibilidade individual por {@link Tarefa#tiposAceitos()}.
     */
    public ResultadoAlocacao alocar(
            SemanaPlanejada semanaPlanejada,
            List<Tarefa> tarefas,
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesPorFaixa) {
        Objects.requireNonNull(
                confiabilidadesPorFaixa, "confiabilidadesPorFaixa não pode ser nulo");
        return alocarInterno(semanaPlanejada, tarefas, Map.copyOf(confiabilidadesPorFaixa));
    }

    private ResultadoAlocacao alocarInterno(
            SemanaPlanejada semanaPlanejada,
            List<Tarefa> tarefas,
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesOuNulo) {
        Objects.requireNonNull(semanaPlanejada, "semanaPlanejada não pode ser nula");
        Objects.requireNonNull(tarefas, "tarefas não pode ser nulo");

        List<Tarefa> tarefasCopia = List.copyOf(tarefas);
        List<Bloco> blocosAjustados = aplicarFolgaMinima(
                semanaPlanejada.semana().blocos(), semanaPlanejada.intervalosOcupados());

        ResultadoAlocacao resultadoBruto = confiabilidadesOuNulo == null
                ? alocadorStrategy.alocar(blocosAjustados, tarefasCopia)
                : alocadorStrategy.alocar(blocosAjustados, tarefasCopia, confiabilidadesOuNulo);
        Objects.requireNonNull(resultadoBruto, "resultado da estratégia não pode ser nulo");

        Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesEfetivas = confiabilidadesOuNulo != null
                ? confiabilidadesOuNulo
                : Objects.requireNonNullElse(
                        alocadorStrategy.confiabilidadesConfiguradas(), Map.of());

        return garantirCompatibilidadeIndividual(resultadoBruto, confiabilidadesEfetivas);
    }

    private List<Bloco> aplicarFolgaMinima(
            List<Bloco> blocos, List<IntervaloOcupado> intervalosOcupados) {
        if (folgaMinima.isZero() || intervalosOcupados.isEmpty() || blocos.isEmpty()) {
            return blocos;
        }

        Map<LocalDate, List<IntervaloOcupado>> ocupadosPorData = agruparPorData(intervalosOcupados);
        long folgaNanos = folgaMinima.toNanos();

        List<Bloco> ajustados = new ArrayList<>(blocos.size());
        for (Bloco bloco : blocos) {
            List<IntervaloOcupado> ocupadosDoDia = ocupadosPorData.getOrDefault(bloco.data(), List.of());
            Bloco elegivel = ajustarBlocoComFolga(bloco, ocupadosDoDia, folgaNanos);
            if (elegivel != null) {
                ajustados.add(elegivel);
            }
        }
        return List.copyOf(ajustados);
    }

    private static Map<LocalDate, List<IntervaloOcupado>> agruparPorData(
            List<IntervaloOcupado> intervalosOcupados) {
        Map<LocalDate, List<IntervaloOcupado>> porData = new HashMap<>();
        for (IntervaloOcupado ocupado : intervalosOcupados) {
            porData.computeIfAbsent(ocupado.data(), ignored -> new ArrayList<>()).add(ocupado);
        }
        return porData;
    }

    private static Bloco ajustarBlocoComFolga(
            Bloco bloco, List<IntervaloOcupado> ocupadosDoDia, long folgaNanos) {
        if (ocupadosDoDia.isEmpty()) {
            return bloco;
        }

        long blocoInicio = bloco.inicio().toNanoOfDay();
        long blocoFim = bloco.fim().toNanoOfDay();
        boolean adjacenteAntes = false;
        boolean adjacenteDepois = false;

        for (IntervaloOcupado ocupado : ocupadosDoDia) {
            long ocupadoInicio = ocupado.inicio().toNanoOfDay();
            // LocalTime.MIDNIGHT como fim exclusivo de IntervaloOcupado representa 24:00 do dia.
            long ocupadoFim = ocupado.fim().equals(LocalTime.MIDNIGHT)
                    ? NANOS_POR_DIA
                    : ocupado.fim().toNanoOfDay();

            if (ocupadoFim == blocoInicio) {
                adjacenteAntes = true;
            }
            if (ocupadoInicio == blocoFim) {
                adjacenteDepois = true;
            }
        }

        if (!adjacenteAntes && !adjacenteDepois) {
            return bloco;
        }

        long inicioEfetivo = adjacenteAntes
                ? (folgaNanos >= NANOS_POR_DIA - blocoInicio ? NANOS_POR_DIA : blocoInicio + folgaNanos)
                : blocoInicio;
        long fimEfetivo = adjacenteDepois
                ? (folgaNanos >= blocoFim ? 0L : blocoFim - folgaNanos)
                : blocoFim;

        if (fimEfetivo <= inicioEfetivo) {
            return null;
        }
        return new Bloco(
                bloco.data(),
                LocalTime.ofNanoOfDay(inicioEfetivo),
                LocalTime.ofNanoOfDay(fimEfetivo),
                bloco.tipo(),
                bloco.descricao());
    }

    private static ResultadoAlocacao garantirCompatibilidadeIndividual(
            ResultadoAlocacao resultadoBruto,
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidades) {
        List<PedacoAlocado> alocadosCompativeis = new ArrayList<>(resultadoBruto.alocados().size());
        List<Pedaco> naoAlocados = new ArrayList<>();

        for (PedacoAlocado alocado : resultadoBruto.alocados()) {
            if (alocadoCompativel(alocado, confiabilidades)) {
                alocadosCompativeis.add(alocado);
            } else {
                naoAlocados.add(alocado.pedaco());
            }
        }

        if (naoAlocados.isEmpty()) {
            return resultadoBruto;
        }
        naoAlocados.addAll(resultadoBruto.naoAlocados());
        return new ResultadoAlocacao(alocadosCompativeis, naoAlocados);
    }

    private static boolean alocadoCompativel(
            PedacoAlocado alocado, Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidades) {
        Bloco blocoOrigem = alocado.blocoOrigem();
        Tarefa tarefa = alocado.pedaco().tarefa();
        if (blocoOrigem.tipo() == TipoBloco.PROFUNDO && !confiabilidades.isEmpty()) {
            FaixaHorario faixa = FaixaHorario.de(
                    blocoOrigem.data().getDayOfWeek(), blocoOrigem.inicio());
            ConfiabilidadeFaixa confiabilidade = confiabilidades.get(faixa);
            if (confiabilidade != null && confiabilidade.baixaConfiabilidade()) {
                return tarefa.tiposAceitos().contains(TipoBloco.INTERROMPIVEL);
            }
        }
        return tarefa.tiposAceitos().contains(blocoOrigem.tipo());
    }
}
