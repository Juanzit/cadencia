package dev.cadencia.agenda;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Janela contínua de tempo livre em um único dia da semana na convenção semiaberta
 * {@code [inicio, fim)}.
 */
public record IntervaloLivre(
        LocalDate data,
        LocalTime inicio,
        LocalTime fim) {

    public IntervaloLivre {
        Objects.requireNonNull(data, "data não pode ser nula");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(fim, "fim não pode ser nulo");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser estritamente posterior a inicio");
        }
    }

    /**
     * Retorna a duração positiva da janela livre entre {@code inicio} e {@code fim}.
     */
    public Duration duracao() {
        return Duration.between(inicio, fim);
    }
}
