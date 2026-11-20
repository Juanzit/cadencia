/**
 * Módulo de acompanhamento de turmas e previsão de transições de módulo.
 *
 * <p>Abriga futuramente a porta {@code TurmaRepository}, o adapter CSV ({@code CsvTurmaRepository},
 * com validação linha a linha) e o serviço {@code PrevisaoTurmaService}.
 *
 * <p>Opera exclusivamente por aritmética determinística de datas a partir do CSV mensal (sem OCR ou
 * leitura automática de capturas de tela), sinalizando possíveis semanas livres por transição de
 * módulo no plano semanal.
 */
package dev.cadencia.turmas;
