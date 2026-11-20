/**
 * Módulo de geração opcional de textos auxiliares via modelo local (Ollama).
 *
 * <p>Reúne futuramente o contrato {@code GeradorTexto} e suas implementações
 * ({@code OllamaGeradorTexto} e {@code FakeGeradorTexto} para testes), além de {@code PdfExtrator}
 * e {@code RoteiroService} para roteiros de aula e resumos semanais.
 *
 * <p><b>Degradação graciosa:</b> atua apenas na geração textual e nunca no planejamento
 * determinístico; a indisponibilidade do Ollama gera aviso e marcação no texto sem interromper o
 * comando principal.
 */
package dev.cadencia.ia;
