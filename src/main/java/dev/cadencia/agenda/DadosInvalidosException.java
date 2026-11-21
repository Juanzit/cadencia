package dev.cadencia.agenda;

/**
 * Exceção lançada quando um arquivo de entrada está ilegível, malformado ou inválido.
 */
public class DadosInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }

    public DadosInvalidosException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
