package dev.cadencia.dominio;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Bloco manual recorrente por dia da semana configurado na agenda (sem data específica).
 */
public record BlocoManual(
        DayOfWeek diaSemana,
        LocalTime inicio,
        LocalTime fim,
        TipoBloco tipo,
        String descricao) {

    public BlocoManual {
        Objects.requireNonNull(diaSemana, "diaSemana não pode ser nulo");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(fim, "fim não pode ser nulo");
        Objects.requireNonNull(tipo, "tipo não pode ser nulo");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser estritamente posterior a inicio");
        }
    }
}
