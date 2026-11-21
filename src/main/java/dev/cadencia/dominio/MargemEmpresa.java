package dev.cadencia.dominio;

/**
 * Margens configuráveis em minutos antes e depois das aulas de uma empresa.
 */
public record MargemEmpresa(
        int antesMin,
        int depoisMin) {

    public MargemEmpresa {
        if (antesMin < 0) {
            throw new IllegalArgumentException("antesMin não pode ser negativo");
        }
        if (depoisMin < 0) {
            throw new IllegalArgumentException("depoisMin não pode ser negativo");
        }
    }
}
