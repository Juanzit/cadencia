package dev.cadencia.dominio;

/**
 * Limites mínimos em minutos para classificação dos intervalos livres em blocos.
 */
public record Classificacao(
        int profundoMinimoMin,
        int interrompivelMinimoMin) {

    public Classificacao {
        if (profundoMinimoMin <= 0) {
            throw new IllegalArgumentException("profundoMinimoMin deve ser estritamente positivo");
        }
        if (interrompivelMinimoMin <= 0) {
            throw new IllegalArgumentException("interrompivelMinimoMin deve ser estritamente positivo");
        }
    }
}
