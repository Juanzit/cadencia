package dev.cadencia.dominio;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Horário diário de funcionamento configurado para a jornada semanal, sem cruzar a meia-noite.
 */
public record Jornada(
        LocalTime inicio,
        LocalTime fim) {

    public Jornada {
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(fim, "fim não pode ser nulo");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser estritamente posterior a inicio");
        }
    }
}
