package dev.cadencia.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Representação imutável do feedback semanal consolidado para uma semana ISO específica.
 */
public record FeedbackSemanal(
        int ano,
        int numeroIso,
        List<ItemFeedback> itens,
        List<String> notasGerais) {

    /** Comparador determinístico por ordem cronológica crescente {@code (ano, numeroIso)}. */
    public static final Comparator<FeedbackSemanal> ORDEM_CRONOLOGICA = Comparator
            .comparingInt(FeedbackSemanal::ano)
            .thenComparingInt(FeedbackSemanal::numeroIso);

    private static final int ANO_MINIMO = 2000;
    private static final int SEMANA_ISO_MINIMA = 1;
    private static final int SEMANA_ISO_MAXIMA = 53;

    public FeedbackSemanal {
        if (ano < ANO_MINIMO) {
            throw new IllegalArgumentException("ano deve ser maior ou igual a 2000");
        }
        if (numeroIso < SEMANA_ISO_MINIMA || numeroIso > SEMANA_ISO_MAXIMA) {
            throw new IllegalArgumentException("numeroIso deve estar entre 1 e 53");
        }
        Objects.requireNonNull(itens, "itens não pode ser nulo");
        Objects.requireNonNull(notasGerais, "notasGerais não pode ser nulo");
        itens = List.copyOf(itens);
        notasGerais = List.copyOf(notasGerais);
    }

    /**
     * Indica se este feedback pertence a uma semana ISO estritamente anterior à {@code semanaAlvo}.
     */
    public boolean ehAnteriorA(Semana semanaAlvo) {
        Objects.requireNonNull(semanaAlvo, "semanaAlvo não pode ser nula");
        return ano < semanaAlvo.ano()
                || (ano == semanaAlvo.ano() && numeroIso < semanaAlvo.numeroIso());
    }
}
