package dev.cadencia.agenda;

import dev.cadencia.dominio.Semana;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável da semana ISO planejada com seus blocos classificados e avisos gerados.
 */
public record SemanaPlanejada(
        LocalDate inicioSemana,
        Semana semana,
        List<String> avisos) {

    public SemanaPlanejada {
        Objects.requireNonNull(inicioSemana, "inicioSemana não pode ser nula");
        Objects.requireNonNull(semana, "semana não pode ser nula");
        Objects.requireNonNull(avisos, "avisos não pode ser nulo");
        avisos = List.copyOf(avisos);
    }
}
