/**
 * Módulo de registro de feedback semanal e cálculo de confiabilidade de horários.
 *
 * <p>Abriga futuramente a porta {@code FeedbackRepository}, o adapter Markdown
 * ({@code MarkdownFeedbackRepository}) para leitura e gravação de status por bloco
 * ({@code CONCLUIDO}, {@code INTERROMPIDO}, {@code NAO_ACONTECEU}) com notas de retomada, e o
 * serviço {@code ConfiabilidadeService}.
 *
 * <p>Consolida o histórico real por faixa de horário de forma determinística para orientar futuras
 * alocações de estudo.
 */
package dev.cadencia.feedback;
