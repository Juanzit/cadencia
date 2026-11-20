/**
 * Módulo de exportação do plano semanal e demais saídas estruturadas do sistema.
 *
 * <p>Separa a lógica de negócio do planejamento da camada de apresentação em arquivo, garantindo
 * geração em Markdown padrão conforme {@code dev/DESIGN.md}, codificação UTF-8 com quebra LF,
 * nomes em kebab-case, legibilidade e saída estritamente idempotente para entradas iguais.
 */
package dev.cadencia.exportacao;
