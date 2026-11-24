package dev.cadencia.cli;

import dev.cadencia.agenda.DadosInvalidosException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Funções compartilhadas de interpretação e validação sintática dos parâmetros da CLI.
 */
final class CliValidacoes {

    private static final String PADRAO_DATA_ISO = "\\d{4}-\\d{2}-\\d{2}";

    private CliValidacoes() {
    }

    static LocalDate interpretarData(String valor) {
        String limpo = valor.trim();
        if (!limpo.matches(PADRAO_DATA_ISO)) {
            throw new DadosInvalidosException(String.format(
                    "Data inválida para --data: \"%s\". Use o formato AAAA-MM-DD (ex.: 2026-11-23).",
                    valor));
        }
        try {
            return LocalDate.parse(limpo);
        } catch (DateTimeParseException ex) {
            throw new DadosInvalidosException(String.format(
                    "Data inválida para --data: \"%s\". Informe uma data existente no calendário no formato AAAA-MM-DD.",
                    valor), ex);
        }
    }

    static void validarCaminhoNaoEmBranco(Path caminho, String nomeOpcao) {
        if (caminho != null && caminho.toString().isBlank()) {
            throw new DadosInvalidosException(String.format(
                    "Caminho inválido para %s: informe o caminho de um arquivo YAML válido.",
                    nomeOpcao));
        }
    }
}
