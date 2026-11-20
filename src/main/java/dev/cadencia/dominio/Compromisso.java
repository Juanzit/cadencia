package dev.cadencia.dominio;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Compromisso fixo semanal modelado como horário de início e duração (ADR-010).
 *
 * <p>Adota convenção semiaberta {@code [inicio, fim)}: a duração deve ser estritamente maior que
 * zero e estritamente menor que 24 horas, permitindo compromissos que atravessam a meia-noite.
 */
public record Compromisso(
        DayOfWeek diaSemana,
        LocalTime inicio,
        Duration duracao,
        TipoCompromisso tipo,
        String empresa,
        String local,
        String titulo) {

    private static final Duration DURACAO_MAXIMA_EXCLUSIVA = Duration.ofHours(24);
    private static final long NANOS_POR_DIA = DURACAO_MAXIMA_EXCLUSIVA.toNanos();

    public Compromisso {
        Objects.requireNonNull(diaSemana, "diaSemana não pode ser nulo");
        Objects.requireNonNull(inicio, "inicio não pode ser nulo");
        Objects.requireNonNull(duracao, "duracao não pode ser nula");
        Objects.requireNonNull(tipo, "tipo não pode ser nulo");
        if (duracao.isZero() || duracao.isNegative() || duracao.compareTo(DURACAO_MAXIMA_EXCLUSIVA) >= 0) {
            throw new IllegalArgumentException("duracao deve ser maior que zero e menor que 24 horas");
        }
    }

    /**
     * Calcula o horário de término derivado de {@code inicio + duracao}.
     *
     * <p>Para compromissos que cruzam ou terminam exatamente à meia-noite, o valor retornado é
     * ajustado ao ciclo de 24 horas do dia (por exemplo, {@code 01:00} ou {@code 00:00}).
     */
    public LocalTime fim() {
        return inicio.plus(duracao);
    }

    /**
     * Indica se o compromisso ultrapassa efetivamente a fronteira de {@code 00:00} para o dia
     * seguinte. Compromissos que terminam exatamente às {@code 00:00} retornam {@code false}.
     */
    public boolean cruzaMeiaNoite() {
        return duracao.toNanos() > nanosRestantesAteMeiaNoite();
    }

    /**
     * Divide o compromisso em fragmentos diários contidos no intervalo {@code [inicio, 00:00)} do
     * dia original e, quando cruza a meia-noite, {@code [00:00, fim)} do dia seguinte.
     *
     * <p>Quando ocorre no domingo, ambos os fragmentos (domingo e segunda-feira) são preservados;
     * o descarte da fração da segunda-feira na semana atual e o registro de aviso cabem à camada
     * de serviço.
     */
    public List<Compromisso> fatiarPorDia() {
        if (!cruzaMeiaNoite()) {
            return List.of(this);
        }
        Duration primeiraDuracao = Duration.ofNanos(nanosRestantesAteMeiaNoite());
        Duration segundaDuracao = duracao.minus(primeiraDuracao);

        Compromisso primeiro = new Compromisso(
                diaSemana, inicio, primeiraDuracao, tipo, empresa, local, titulo);
        Compromisso segundo = new Compromisso(
                diaSemana.plus(1), LocalTime.MIDNIGHT, segundaDuracao, tipo, empresa, local, titulo);
        return List.of(primeiro, segundo);
    }

    private long nanosRestantesAteMeiaNoite() {
        return NANOS_POR_DIA - inicio.toNanoOfDay();
    }
}
