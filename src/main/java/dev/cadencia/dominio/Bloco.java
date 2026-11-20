package dev.cadencia.dominio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Intervalo livre classificado em um único dia da semana, sem cruzar a meia-noite.
 */
public record Bloco(
        LocalDate data,
        LocalTime inicio,
        LocalTime fim,
        TipoBloco tipo,
        String descricao) {

    public Bloco {
        Objects.requireNonNull(data, "data não pode ser nula");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(fim, "fim não pode ser nulo");
        Objects.requireNonNull(tipo, "tipo não pode ser nulo");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser estritamente posterior a inicio");
        }
    }
}
