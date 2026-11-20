/**
 * Contratos e implementações de exportadores de saída (Strategy Pattern).
 *
 * <p>Define a interface {@code ExportadorStrategy} e suas implementações (como
 * {@code MarkdownSemanalExportador}), preparando o Cadência para suportar novos formatos futuros
 * (por exemplo, JSON ou {@code .ics}) sem alterar os serviços de agenda e planejamento.
 */
package dev.cadencia.exportacao.strategies;
