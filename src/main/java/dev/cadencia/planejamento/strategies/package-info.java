/**
 * Estratégias intercambiáveis de alocação de estudos (Strategy Pattern).
 *
 * <p>Define o contrato {@code AlocadorStrategy} e suas implementações determinísticas (como
 * {@code PrioridadeAlocadorStrategy} e {@code ConfiabilidadeAlocadorStrategy}), permitindo evoluir
 * as heurísticas de distribuição sem alterar {@code PlanejamentoService}.
 *
 * <p><b>Regra determinística:</b> nenhuma decisão de alocação pode depender de modelos de IA/LLM
 * ou fontes não determinísticas.
 */
package dev.cadencia.planejamento.strategies;
