package dev.cadencia.agenda;

import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.Semana;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável da semana ISO planejada com seus blocos classificados, ocupações, avisos
 * gerados e configuração de classificação aplicada.
 */
public record SemanaPlanejada(
        LocalDate inicioSemana,
        Semana semana,
        List<IntervaloOcupado> intervalosOcupados,
        List<String> avisos,
        AgendaConfiguracao configuracao) {

    public SemanaPlanejada {
        Objects.requireNonNull(inicioSemana, "inicioSemana não pode ser nula");
        Objects.requireNonNull(semana, "semana não pode ser nula");
        Objects.requireNonNull(intervalosOcupados, "intervalosOcupados não pode ser nulo");
        Objects.requireNonNull(avisos, "avisos não pode ser nulo");
        intervalosOcupados = List.copyOf(intervalosOcupados);
        avisos = List.copyOf(avisos);
    }

    /**
     * Construtor de conveniência sem referência explícita à configuração da agenda.
     */
    public SemanaPlanejada(
            LocalDate inicioSemana,
            Semana semana,
            List<IntervaloOcupado> intervalosOcupados,
            List<String> avisos) {
        this(inicioSemana, semana, intervalosOcupados, avisos, null);
    }

    /**
     * Construtor de conveniência sem lista explícita de intervalos ocupados nem configuração.
     */
    public SemanaPlanejada(LocalDate inicioSemana, Semana semana, List<String> avisos) {
        this(inicioSemana, semana, List.of(), avisos, null);
    }
}
