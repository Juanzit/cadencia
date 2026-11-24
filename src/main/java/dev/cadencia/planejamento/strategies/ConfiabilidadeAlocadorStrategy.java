package dev.cadencia.planejamento.strategies;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Estratégia determinística de alocação sensível à confiabilidade histórica das faixas de horário:
 * preserva integralmente a heurística de prioridade da Fase 3 quando uma faixa não possui registro
 * ou não está em baixa confiabilidade, e restringe blocos {@link TipoBloco#PROFUNDO} em faixas de
 * baixa confiabilidade ({@link ConfiabilidadeFaixa#baixaConfiabilidade()}) para aceitarem apenas
 * tarefas compatíveis com {@link TipoBloco#INTERROMPIVEL}, bloqueando tarefas exclusivamente
 * {@code PROFUNDO}.
 */
public class ConfiabilidadeAlocadorStrategy implements AlocadorStrategy {

    private final Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesPadrao;

    public ConfiabilidadeAlocadorStrategy() {
        this(Map.of());
    }

    public ConfiabilidadeAlocadorStrategy(
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesPorFaixa) {
        Objects.requireNonNull(
                confiabilidadesPorFaixa, "confiabilidadesPorFaixa não pode ser nulo");
        this.confiabilidadesPadrao = Map.copyOf(confiabilidadesPorFaixa);
    }

    /**
     * Cria uma estratégia configurada com cópia defensiva imutável dos índices por
     * {@link FaixaHorario}.
     */
    public static ConfiabilidadeAlocadorStrategy comConfiabilidade(
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesPorFaixa) {
        return new ConfiabilidadeAlocadorStrategy(confiabilidadesPorFaixa);
    }

    @Override
    public Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesConfiguradas() {
        return confiabilidadesPadrao;
    }

    @Override
    public ResultadoAlocacao alocar(List<Bloco> blocosDisponiveis, List<Tarefa> tarefas) {
        return alocar(blocosDisponiveis, tarefas, confiabilidadesPadrao);
    }

    @Override
    public ResultadoAlocacao alocar(
            List<Bloco> blocosDisponiveis,
            List<Tarefa> tarefas,
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesPorFaixa) {
        Objects.requireNonNull(
                confiabilidadesPorFaixa, "confiabilidadesPorFaixa não pode ser nulo");
        Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesCopia =
                Map.copyOf(confiabilidadesPorFaixa);
        return PrioridadeAlocadorStrategy.alocarComCompatibilidade(
                blocosDisponiveis,
                tarefas,
                (bloco, tarefa) -> blocoAceitaTarefa(bloco, tarefa, confiabilidadesCopia));
    }

    private static boolean blocoAceitaTarefa(
            Bloco bloco, Tarefa tarefa, Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidades) {
        if (bloco.tipo() == TipoBloco.PROFUNDO) {
            FaixaHorario faixa = FaixaHorario.de(bloco.data().getDayOfWeek(), bloco.inicio());
            ConfiabilidadeFaixa confiabilidade = confiabilidades.get(faixa);
            if (confiabilidade != null && confiabilidade.baixaConfiabilidade()) {
                return tarefa.tiposAceitos().contains(TipoBloco.INTERROMPIVEL);
            }
        }
        return tarefa.tiposAceitos().contains(bloco.tipo());
    }
}
