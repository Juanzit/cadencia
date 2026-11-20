/**
 * Módulo de agenda fixa e cálculo dos blocos livres da semana.
 *
 * <p>Reúne futuramente o DTO com Bean Validation ({@code AgendaDto}), a porta
 * {@code AgendaRepository}, o adapter YAML ({@code YamlAgendaRepository}, com tratamento separado
 * de erros de sintaxe via {@code JsonLocation} e de validação por caminho de campo) e o serviço
 * {@code AgendaService}.
 *
 * <p>Concentra a aplicação de margens configuráveis (preparo, feedback e deslocamento), o
 * fatiamento de compromissos que cruzam a meia-noite com {@code Clock} injetado e a classificação
 * dos intervalos livres em {@code PROFUNDO}, {@code INTERROMPIVEL} e {@code VIGILANCIA}.
 */
package dev.cadencia.agenda;
