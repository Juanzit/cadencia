package dev.cadencia.agenda;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Fragmento ocupado de um único dia da semana na convenção semiaberta {@code [inicio, fim)}.
 */
public record IntervaloOcupado(
        LocalDate data,
        LocalTime inicio,
        Duration duracao,
        TipoIntervaloOcupado tipo,
        String descricao) {

    private static final long NANOS_POR_DIA = Duration.ofDays(1).toNanos();

    public IntervaloOcupado {
        Objects.requireNonNull(data, "data não pode ser nula");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(duracao, "duracao não pode ser nula");
        Objects.requireNonNull(tipo, "tipo não pode ser nulo");
        Objects.requireNonNull(descricao, "descricao não pode ser nula");

        long nanosDisponiveisNoDia = NANOS_POR_DIA - inicio.toNanoOfDay();
        if (duracao.isZero() || duracao.isNegative() || duracao.toNanos() > nanosDisponiveisNoDia) {
            throw new IllegalArgumentException(
                    "duracao deve ser positiva e caber dentro do mesmo dia até 00:00");
        }
    }

    /**
     * Calcula o horário de término exclusivo derivado de {@code inicio + duracao}.
     */
    public LocalTime fim() {
        return inicio.plus(duracao);
    }
}
