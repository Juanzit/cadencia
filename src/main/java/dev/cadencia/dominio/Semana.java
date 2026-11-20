package dev.cadencia.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Representação imutável de uma semana ISO planejada com seus compromissos e blocos livres.
 */
public record Semana(
        int ano,
        int numeroIso,
        List<Compromisso> compromissos,
        List<Bloco> blocos) {

    public Semana {
        if (numeroIso < 1 || numeroIso > 53) {
            throw new IllegalArgumentException("numeroIso deve estar entre 1 e 53");
        }
        Objects.requireNonNull(compromissos, "compromissos não pode ser nulo");
        Objects.requireNonNull(blocos, "blocos não pode ser nulo");
        compromissos = List.copyOf(compromissos);
        blocos = List.copyOf(blocos);
    }
}
