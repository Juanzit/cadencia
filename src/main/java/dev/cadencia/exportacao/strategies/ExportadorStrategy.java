package dev.cadencia.exportacao.strategies;

import dev.cadencia.agenda.SemanaPlanejada;

/**
 * Contrato do padrão Strategy para exportação de semanas planejadas em diferentes formatos de
 * saída.
 */
public interface ExportadorStrategy {

    /**
     * Serializa a semana planejada em uma representação textual determinística no formato desta
     * estratégia.
     */
    String exportar(SemanaPlanejada semanaPlanejada);
}
