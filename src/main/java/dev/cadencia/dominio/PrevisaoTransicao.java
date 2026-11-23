package dev.cadencia.dominio;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Objects;

/**
 * Representa um marco previsto de encerramento ou transição de módulo de uma {@link Turma}
 * em uma semana ISO-8601 específica.
 *
 * @param turma          turma associada ao evento previsto
 * @param tipo           tipo do marco semanal ({@link TipoEventoTurma})
 * @param anoIso         ano ISO-8601 da semana afetada
 * @param semanaIso      número da semana ISO-8601 (1 a 53)
 * @param dataReferencia data de referência do marco (fim do módulo ou transição prevista)
 */
public record PrevisaoTransicao(
        Turma turma,
        TipoEventoTurma tipo,
        int anoIso,
        int semanaIso,
        LocalDate dataReferencia) {

    public PrevisaoTransicao {
        Objects.requireNonNull(turma, "turma não pode ser nula.");
        Objects.requireNonNull(tipo, "tipo não pode ser nulo.");
        if (anoIso < 1) {
            throw new IllegalArgumentException("anoIso deve ser maior ou igual a 1.");
        }
        if (semanaIso < 1 || semanaIso > 53) {
            throw new IllegalArgumentException("semanaIso deve estar entre 1 e 53.");
        }
        Objects.requireNonNull(dataReferencia, "dataReferencia não pode ser nula.");
        int anoData = dataReferencia.get(IsoFields.WEEK_BASED_YEAR);
        int semanaData = dataReferencia.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (anoIso != anoData || semanaIso != semanaData) {
            throw new IllegalArgumentException(
                    "dataReferencia deve pertencer à semana ISO "
                            + anoIso
                            + "-W"
                            + String.format("%02d", semanaIso)
                            + ".");
        }
        LocalDate dataEsperada = switch (tipo) {
            case ENCERRAMENTO_MODULO -> turma.dataFimModulo();
            case TRANSICAO_PREVISTA -> turma.dataPrevistaTransicao();
        };
        if (!dataReferencia.equals(dataEsperada)) {
            throw new IllegalArgumentException(
                    "dataReferencia deve corresponder à data exata do evento informado ("
                            + dataEsperada
                            + ").");
        }
    }
}
