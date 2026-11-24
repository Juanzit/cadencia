package dev.cadencia.exportacao.strategies;

import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.util.Map;

/**
 * Contrato do padrão Strategy para exportação de semanas planejadas em diferentes formatos de
 * saída.
 */
public interface ExportadorStrategy {

    /**
     * Serializa a semana planejada em uma representação textual determinística no formato desta
     * estratégia, sem alocação de backlog.
     */
    String exportar(SemanaPlanejada semanaPlanejada);

    /**
     * Serializa a semana planejada combinando ocupações, blocos livres e os pedaços alocados pelo
     * planejamento determinístico de estudos.
     */
    String exportar(SemanaPlanejada semanaPlanejada, ResultadoAlocacao resultadoAlocacao);

    /**
     * Serializa a semana planejada incluindo eventuais notas {@code "parei aqui"} vigentes para
     * tarefas retomadas a partir de semanas anteriores.
     */
    String exportar(
            SemanaPlanejada semanaPlanejada,
            ResultadoAlocacao resultadoAlocacao,
            Map<String, String> notasRetomadaPorTarefa);
}
