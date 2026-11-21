package dev.cadencia.agenda;

import dev.cadencia.dominio.AgendaConfiguracao;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável da expansão semanal da agenda com ocupações, configuração e avisos.
 */
public record AgendaExpandida(
        LocalDate inicioSemana,
        AgendaConfiguracao configuracao,
        List<IntervaloOcupado> intervalosOcupados,
        List<String> avisos) {

    public AgendaExpandida {
        Objects.requireNonNull(inicioSemana, "inicioSemana não pode ser nula");
        Objects.requireNonNull(configuracao, "configuracao não pode ser nula");
        Objects.requireNonNull(intervalosOcupados, "intervalosOcupados não pode ser nulo");
        Objects.requireNonNull(avisos, "avisos não pode ser nulo");
        intervalosOcupados = List.copyOf(intervalosOcupados);
        avisos = List.copyOf(avisos);
    }
}
