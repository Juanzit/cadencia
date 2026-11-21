package dev.cadencia.agenda;

import dev.cadencia.dominio.AgendaConfiguracao;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável do cálculo semanal de intervalos livres dentro da jornada configurada.
 */
public record AgendaIntervalosLivres(
        LocalDate inicioSemana,
        AgendaConfiguracao configuracao,
        List<IntervaloLivre> intervalosLivres,
        List<String> avisos) {

    public AgendaIntervalosLivres {
        Objects.requireNonNull(inicioSemana, "inicioSemana não pode ser nula");
        Objects.requireNonNull(configuracao, "configuracao não pode ser nula");
        Objects.requireNonNull(intervalosLivres, "intervalosLivres não pode ser nulo");
        Objects.requireNonNull(avisos, "avisos não pode ser nulo");
        intervalosLivres = List.copyOf(intervalosLivres);
        avisos = List.copyOf(avisos);
    }
}
