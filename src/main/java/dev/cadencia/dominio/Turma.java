package dev.cadencia.dominio;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Representa uma turma acompanhada pelo calendário de módulos e suas datas previstas
 * de encerramento e transição.
 *
 * @param codigo         código identificador da turma
 * @param empresa        empresa ou cliente associado à turma
 * @param moduloAtual    nome ou identificador do módulo em curso
 * @param inicioModulo   data de início do módulo atual
 * @param duracaoSemanas duração prevista do módulo em semanas (maior que zero)
 */
public record Turma(
        String codigo,
        String empresa,
        String moduloAtual,
        LocalDate inicioModulo,
        int duracaoSemanas) {

    public Turma {
        codigo = exigirTexto(codigo, "codigo");
        empresa = exigirTexto(empresa, "empresa");
        moduloAtual = exigirTexto(moduloAtual, "moduloAtual");
        Objects.requireNonNull(inicioModulo, "inicioModulo não pode ser nulo.");
        if (duracaoSemanas <= 0) {
            throw new IllegalArgumentException("duracaoSemanas deve ser maior que zero.");
        }
    }

    /**
     * Calcula o último dia previsto para o módulo atual.
     *
     * @return {@code inicioModulo.plusWeeks(duracaoSemanas).minusDays(1)}
     */
    public LocalDate dataFimModulo() {
        return inicioModulo.plusWeeks(duracaoSemanas).minusDays(1);
    }

    /**
     * Calcula a data prevista de transição imediatamente posterior ao fim do módulo atual.
     *
     * @return {@code inicioModulo.plusWeeks(duracaoSemanas)}
     */
    public LocalDate dataPrevistaTransicao() {
        return inicioModulo.plusWeeks(duracaoSemanas);
    }

    /**
     * Retorna a semana ISO-8601 que contém {@link #dataFimModulo()}.
     *
     * @return semana ISO correspondente ao encerramento do módulo
     */
    public Semana semanaEncerramento() {
        return semanaDe(dataFimModulo());
    }

    /**
     * Retorna a semana ISO-8601 que contém {@link #dataPrevistaTransicao()}.
     *
     * @return semana ISO correspondente à transição prevista
     */
    public Semana semanaTransicaoPrevista() {
        return semanaDe(dataPrevistaTransicao());
    }

    /**
     * Verifica se a semana informada contém algum marco previsto desta turma.
     *
     * <p>Quando o encerramento e a transição prevista coincidem na mesma semana ISO (o que
     * ocorre quando {@code inicioModulo} não cai em uma segunda-feira), o retorno prioriza
     * {@link TipoEventoTurma#ENCERRAMENTO_MODULO}.</p>
     *
     * @param semana semana ISO a consultar
     * @return previsão correspondente à semana, ou {@link Optional#empty()} se não houver marco
     */
    public Optional<PrevisaoTransicao> eventoNaSemana(Semana semana) {
        Objects.requireNonNull(semana, "semana não pode ser nula.");
        Semana encerramento = semanaEncerramento();
        if (mesmaSemanaIso(semana, encerramento)) {
            return Optional.of(new PrevisaoTransicao(
                    this,
                    TipoEventoTurma.ENCERRAMENTO_MODULO,
                    encerramento.ano(),
                    encerramento.numeroIso(),
                    dataFimModulo()));
        }
        Semana transicao = semanaTransicaoPrevista();
        if (mesmaSemanaIso(semana, transicao)) {
            return Optional.of(new PrevisaoTransicao(
                    this,
                    TipoEventoTurma.TRANSICAO_PREVISTA,
                    transicao.ano(),
                    transicao.numeroIso(),
                    dataPrevistaTransicao()));
        }
        return Optional.empty();
    }

    private static String exigirTexto(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo.");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode estar em branco.");
        }
        return valor.trim();
    }

    private static Semana semanaDe(LocalDate data) {
        return new Semana(
                data.get(IsoFields.WEEK_BASED_YEAR),
                data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                List.of(),
                List.of());
    }

    private static boolean mesmaSemanaIso(Semana a, Semana b) {
        return a.ano() == b.ano() && a.numeroIso() == b.numeroIso();
    }
}
