package dev.cadencia.dominio;

/**
 * Status real de execução registrado para um bloco ou estudo no feedback semanal.
 */
public enum StatusBloco {
    CONCLUIDO(1.0),
    INTERROMPIDO(0.5),
    NAO_ACONTECEU(0.0);

    private final double peso;

    StatusBloco(double peso) {
        this.peso = peso;
    }

    /**
     * Peso estatístico determinístico utilizado no cálculo de confiabilidade da faixa de horário.
     */
    public double peso() {
        return peso;
    }
}
