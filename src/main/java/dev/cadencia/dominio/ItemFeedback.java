package dev.cadencia.dominio;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Registro imutável de execução real de um estudo ou bloco livre em uma semana, podendo carregar
 * a nota de retomada ("parei aqui") quando aplicável.
 */
public record ItemFeedback(
        DayOfWeek diaSemana,
        LocalTime inicio,
        LocalTime fim,
        String titulo,
        StatusBloco status,
        String notaRetomada) {

    public ItemFeedback {
        Objects.requireNonNull(diaSemana, "diaSemana não pode ser nulo");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        if (fim != null && !fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser estritamente posterior a inicio");
        }
        Objects.requireNonNull(titulo, "titulo não pode ser nulo");
        if (titulo.isBlank()) {
            throw new IllegalArgumentException("titulo não pode ser vazio");
        }
        titulo = titulo.trim();
        Objects.requireNonNull(status, "status não pode ser nulo");
        notaRetomada = (notaRetomada == null || notaRetomada.isBlank())
                ? null
                : notaRetomada.trim();
    }

    /**
     * Indica se este item possui uma nota não vazia de retomada ("parei aqui").
     */
    public boolean possuiNotaRetomada() {
        return notaRetomada != null;
    }

    /**
     * Deriva a faixa de horário estatística correspondente ao dia e horário de início do item.
     */
    public FaixaHorario faixaHorario() {
        return FaixaHorario.de(diaSemana, inicio);
    }
}
