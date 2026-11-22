package dev.cadencia.planejamento;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Pedaco;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Representa a alocação determinística de um {@link Pedaco} em uma janela {@code [inicio, fim)}
 * dentro de um {@link Bloco} na data correspondente.
 */
public record PedacoAlocado(
        Bloco blocoOrigem,
        LocalDate data,
        LocalTime inicio,
        LocalTime fim,
        Pedaco pedaco) {

    public PedacoAlocado {
        Objects.requireNonNull(blocoOrigem, "blocoOrigem não pode ser nulo");
        Objects.requireNonNull(data, "data não pode ser nula");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(fim, "fim não pode ser nulo");
        Objects.requireNonNull(pedaco, "pedaco não pode ser nulo");
        if (!data.equals(blocoOrigem.data())) {
            throw new IllegalArgumentException("data deve corresponder à data de blocoOrigem");
        }
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser estritamente posterior a inicio");
        }
        if (inicio.isBefore(blocoOrigem.inicio()) || fim.isAfter(blocoOrigem.fim())) {
            throw new IllegalArgumentException(
                    "intervalo alocado deve estar contido nos limites de blocoOrigem");
        }
        if (!Duration.between(inicio, fim).equals(pedaco.duracao())) {
            throw new IllegalArgumentException(
                    "duração do intervalo alocado deve corresponder à duração do pedaço");
        }
    }
}
