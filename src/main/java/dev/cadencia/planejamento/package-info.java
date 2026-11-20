/**
 * Módulo de alocação determinística de tarefas de estudo nos blocos livres.
 *
 * <p>Abriga futuramente o DTO e a porta de entrada do backlog ({@code BacklogDto} e
 * {@code BacklogRepository}) e o serviço {@code PlanejamentoService}.
 *
 * <p>Separa a orquestração do serviço (garantia de folga mínima entre blocos, divisão em pedaços
 * retomáveis e consistência determinística sem IA) das heurísticas intercambiáveis definidas em
 * {@link dev.cadencia.planejamento.strategies}.
 */
package dev.cadencia.planejamento;
