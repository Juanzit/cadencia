package dev.cadencia.dominio;

import java.time.Duration;
import java.util.Objects;

/**
 * Fração divisível e retomável de uma {@link Tarefa} de estudo.
 */
public record Pedaco(
        Tarefa tarefa,
        int numero,
        int total,
        Duration duracao) {

    public Pedaco {
        Objects.requireNonNull(tarefa, "tarefa não pode ser nula");
        if (total <= 0) {
            throw new IllegalArgumentException("total deve ser estritamente positivo");
        }
        if (numero <= 0 || numero > total) {
            throw new IllegalArgumentException("numero deve estar entre 1 e total");
        }
        Objects.requireNonNull(duracao, "duracao não pode ser nula");
        if (duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException("duracao deve ser estritamente positiva");
        }
    }
}
