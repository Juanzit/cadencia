package dev.cadencia.planejamento.strategies;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contrato do padrão Strategy para alocação determinística de tarefas de estudo em blocos livres
 * disponíveis, sem dependência de modelos de IA.
 */
public interface AlocadorStrategy {

    /**
     * Distribui os pedaços das tarefas informadas entre os blocos disponíveis de acordo com a
     * heurística determinística desta estratégia.
     */
    ResultadoAlocacao alocar(List<Bloco> blocosDisponiveis, List<Tarefa> tarefas);

    /**
     * Distribui os pedaços das tarefas considerando opcionalmente o mapa de confiabilidade histórica
     * por {@link FaixaHorario}; estratégias que não utilizam histórico delegam para
     * {@link #alocar(List, List)}.
     */
    default ResultadoAlocacao alocar(
            List<Bloco> blocosDisponiveis,
            List<Tarefa> tarefas,
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesPorFaixa) {
        Objects.requireNonNull(
                confiabilidadesPorFaixa, "confiabilidadesPorFaixa não pode ser nulo");
        return alocar(blocosDisponiveis, tarefas);
    }

    /**
     * Expõe eventuais índices de confiabilidade previamente vinculados à instância da estratégia,
     * permitindo que {@code PlanejamentoService} valide a compatibilidade sem conhecer subtipos.
     */
    default Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidadesConfiguradas() {
        return Map.of();
    }
}
