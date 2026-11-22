package dev.cadencia.exportacao;

import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resultado imutável da geração e persistência do plano semanal em arquivo.
 */
public record PlanoSemanalGerado(
        Path arquivoSaida,
        SemanaPlanejada semanaPlanejada,
        ResultadoAlocacao resultadoAlocacao) {

    public PlanoSemanalGerado {
        Objects.requireNonNull(arquivoSaida, "arquivoSaida não pode ser nulo");
        Objects.requireNonNull(semanaPlanejada, "semanaPlanejada não pode ser nula");
    }

    /**
     * Construtor de conveniência sem resultado de alocação de backlog.
     */
    public PlanoSemanalGerado(Path arquivoSaida, SemanaPlanejada semanaPlanejada) {
        this(arquivoSaida, semanaPlanejada, null);
    }
}
