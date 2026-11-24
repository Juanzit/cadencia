package dev.cadencia.dominio;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Chave imutável de agrupamento estatístico por dia da semana e período do dia.
 */
public record FaixaHorario(DayOfWeek diaSemana, PeriodoDia periodo) {

    public FaixaHorario {
        Objects.requireNonNull(diaSemana, "diaSemana não pode ser nulo");
        Objects.requireNonNull(periodo, "periodo não pode ser nulo");
    }

    /**
     * Constrói uma {@link FaixaHorario} classificando o {@code horario} via {@link PeriodoDia#de}.
     */
    public static FaixaHorario de(DayOfWeek diaSemana, LocalTime horario) {
        Objects.requireNonNull(diaSemana, "diaSemana não pode ser nulo");
        return new FaixaHorario(diaSemana, PeriodoDia.de(horario));
    }
}
