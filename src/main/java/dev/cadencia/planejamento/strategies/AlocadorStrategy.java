package dev.cadencia.planejamento.strategies;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.util.List;

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
}
