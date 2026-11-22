package dev.cadencia.exportacao;

import dev.cadencia.agenda.SemanaPlanejada;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resultado imutável da geração e persistência do plano semanal em arquivo.
 */
public record PlanoSemanalGerado(Path arquivoSaida, SemanaPlanejada semanaPlanejada) {

    public PlanoSemanalGerado {
        Objects.requireNonNull(arquivoSaida, "arquivoSaida não pode ser nulo");
        Objects.requireNonNull(semanaPlanejada, "semanaPlanejada não pode ser nula");
    }
}
