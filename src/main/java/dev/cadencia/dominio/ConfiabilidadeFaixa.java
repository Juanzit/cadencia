package dev.cadencia.dominio;

import java.util.Objects;

/**
 * Índice estatístico determinístico de confiabilidade de uma {@link FaixaHorario}, consolidando a
 * quantidade de amostras observadas, a média ponderada dos status registrados ({@code [0.0, 1.0]})
 * e os indicadores derivados contra o mínimo de amostras e o limite configurado.
 */
public record ConfiabilidadeFaixa(
        FaixaHorario faixa,
        int amostras,
        double indice,
        boolean amostrasSuficientes,
        boolean baixaConfiabilidade) {

    public ConfiabilidadeFaixa {
        Objects.requireNonNull(faixa, "faixa não pode ser nula");
        if (amostras < 0) {
            throw new IllegalArgumentException("amostras não pode ser negativo");
        }
        if (!Double.isFinite(indice) || indice < 0.0 || indice > 1.0) {
            throw new IllegalArgumentException("indice deve estar entre 0.0 e 1.0");
        }
        if (!amostrasSuficientes && baixaConfiabilidade) {
            throw new IllegalArgumentException(
                    "faixa sem amostras suficientes não pode ser classificada como baixa confiabilidade");
        }
    }

    /**
     * Valida que o mínimo de amostras é estritamente positivo.
     */
    public static void validarMinimoAmostras(int minimoAmostras) {
        if (minimoAmostras <= 0) {
            throw new IllegalArgumentException("minimoAmostras deve ser estritamente positivo");
        }
    }

    /**
     * Valida que o limite de confiabilidade é um número finito no intervalo fechado {@code [0.0, 1.0]}.
     */
    public static void validarLimiteConfiabilidade(double limiteConfiabilidade) {
        if (!Double.isFinite(limiteConfiabilidade)
                || limiteConfiabilidade < 0.0
                || limiteConfiabilidade > 1.0) {
            throw new IllegalArgumentException("limiteConfiabilidade deve estar entre 0.0 e 1.0");
        }
    }
}
