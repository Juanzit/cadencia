/**
 * Camada de entrada em linha de comando (Controller) baseada em Picocli.
 *
 * <p>Responsável exclusivamente por interpretar argumentos e opções, delegar a execução aos
 * serviços de aplicação ({@code *Service}) e formatar saídas ou mensagens de erro e códigos de
 * saída conforme {@code dev/DESIGN.md}.
 *
 * <p><b>Restrições:</b> é proibido concentrar regras de negócio neste pacote ou acessar
 * diretamente repositórios ({@code *Repository}), adapters de persistência ou APIs de leitura e
 * escrita de arquivos.
 */
package dev.cadencia.cli;
