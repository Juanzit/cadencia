package dev.cadencia.dominio;

/**
 * Classifica os marcos semanais de previsão para o andamento de um módulo de turma.
 */
public enum TipoEventoTurma {
    /**
     * Semana ISO em que ocorre o último dia previsto para o módulo atual.
     */
    ENCERRAMENTO_MODULO,

    /**
     * Semana ISO em que ocorre a data prevista para início da transição após o módulo atual.
     */
    TRANSICAO_PREVISTA
}
