package dev.cadencia.agenda;

import dev.cadencia.dominio.Semana;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável da semana ISO planejada com seus blocos classificados, ocupações e avisos
 * gerados.
 */
public record SemanaPlanejada(
        LocalDate inicioSemana,
        Semana semana,
        List<IntervaloOcupado> intervalosOcupados,
        List<String> avisos) {

    public SemanaPlanejada {
        Objects.requireNonNull(inicioSemana, "inicioSemana não pode ser nula");
        Objects.requireNonNull(semana, "semana não pode ser nula");
        Objects.requireNonNull(intervalosOcupados, "intervalosOcupados não pode ser nulo");
        Objects.requireNonNull(avisos, "avisos não pode ser nulo");
        intervalosOcupados = List.copyOf(intervalosOcupados);
        avisos = List.copyOf(avisos);
    }

    /**
     * Construtor de conveniência sem lista explícita de intervalos ocupados.
     */
    public SemanaPlanejada(LocalDate inicioSemana, Semana semana, List<String> avisos) {
        this(inicioSemana, semana, List.of(), avisos);
    }
}
