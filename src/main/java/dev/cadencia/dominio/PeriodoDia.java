package dev.cadencia.dominio;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Faixa de período do dia para agrupamento estatístico de confiabilidade de horários.
 */
public enum PeriodoDia {
    MANHA,
    TARDE,
    NOITE;

    private static final LocalTime INICIO_TARDE = LocalTime.of(12, 0);
    private static final LocalTime INICIO_NOITE = LocalTime.of(18, 0);

    /**
     * Classifica deterministicamente um horário local em {@link #MANHA} ({@code < 12:00}),
     * {@link #TARDE} ({@code 12:00} até {@code 17:59}) ou {@link #NOITE} ({@code >= 18:00}).
     */
    public static PeriodoDia de(LocalTime horario) {
        Objects.requireNonNull(horario, "horario não pode ser nulo");
        if (horario.isBefore(INICIO_TARDE)) {
            return MANHA;
        }
        if (horario.isBefore(INICIO_NOITE)) {
            return TARDE;
        }
        return NOITE;
    }
}
