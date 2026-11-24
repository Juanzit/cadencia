package dev.cadencia.feedback;

import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resultado imutável da geração e gravação do arquivo modelo de feedback semanal.
 */
public record ModeloFeedbackGerado(
        Path arquivoSaida,
        FeedbackSemanal feedbackSemanal,
        SemanaPlanejada semanaPlanejada,
        ResultadoAlocacao resultadoAlocacao) {

    public ModeloFeedbackGerado {
        Objects.requireNonNull(arquivoSaida, "arquivoSaida não pode ser nulo");
        Objects.requireNonNull(feedbackSemanal, "feedbackSemanal não pode ser nulo");
        Objects.requireNonNull(semanaPlanejada, "semanaPlanejada não pode ser nula");
    }

    /**
     * Construtor de conveniência sem resultado de alocação de backlog.
     */
    public ModeloFeedbackGerado(
            Path arquivoSaida, FeedbackSemanal feedbackSemanal, SemanaPlanejada semanaPlanejada) {
        this(arquivoSaida, feedbackSemanal, semanaPlanejada, null);
    }
}
