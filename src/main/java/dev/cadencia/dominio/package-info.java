/**
 * Núcleo puro de domínio do Cadência, composto por {@code record}s imutáveis e invariantes sem
 * efeitos colaterais ({@code Compromisso}, {@code Bloco}, {@code TipoBloco}, {@code Tarefa},
 * {@code Pedaco}, {@code StatusBloco}, {@code PeriodoDia}, {@code FaixaHorario},
 * {@code ItemFeedback}, {@code FeedbackSemanal}, {@code Turma}, {@code Semana}).
 *
 * <p><b>Isolamento estrito:</b> é proibido depender de Spring ({@code org.springframework..}),
 * Jackson ({@code com.fasterxml.jackson..}) ou APIs de I/O ({@code java.io..},
 * {@code java.nio.file..}).
 *
 * <p><b>Manipulação de tempo:</b> utiliza exclusivamente {@code java.time} ({@code LocalDate},
 * {@code LocalTime}, {@code Duration}, {@code ZoneId} com fuso {@code America/Sao_Paulo} e
 * {@code Clock} injetado), sendo proibido usar {@code Date}, {@code Calendar},
 * {@code LocalDateTime} sem fuso ou chamadas diretas a {@code now()}.
 */
package dev.cadencia.dominio;
