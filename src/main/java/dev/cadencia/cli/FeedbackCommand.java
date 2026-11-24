package dev.cadencia.cli;

import dev.cadencia.dominio.Semana;
import dev.cadencia.feedback.FeedbackService;
import dev.cadencia.feedback.ModeloFeedbackGerado;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Subcomando {@code cadencia feedback --modelo} que gera e salva o arquivo modelo Markdown em
 * {@code <diretorioDados>/feedback/AAAA-Wnn.md}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(
        name = "feedback",
        mixinStandardHelpOptions = true,
        description = "Gera o arquivo modelo Markdown de feedback semanal para a semana ISO de referência.")
public class FeedbackCommand implements Callable<Integer> {

    private final FeedbackService feedbackService;

    @Option(
            names = "--modelo",
            required = true,
            description = "Gera o modelo feedback/AAAA-Wnn.md preenchido com os estudos ou blocos da semana.")
    private boolean modelo;

    @Option(
            names = "--data",
            paramLabel = "AAAA-MM-DD",
            description = "Data de referência para selecionar a semana ISO (padrão: data atual do relógio).")
    private String dataTexto;

    @Option(
            names = "--agenda",
            paramLabel = "CAMINHO",
            description = "Caminho alternativo para o arquivo agenda.yaml de entrada.")
    private Path caminhoAgenda;

    @Option(
            names = "--backlog",
            paramLabel = "CAMINHO",
            description = "Caminho alternativo para o arquivo backlog.yaml de entrada.")
    private Path caminhoBacklog;

    @Option(
            names = "--sobrescrever",
            description = "Sobrescreve o arquivo feedback/AAAA-Wnn.md caso ele já exista.")
    private boolean sobrescrever;

    @Spec
    private CommandSpec spec;

    public FeedbackCommand(FeedbackService feedbackService) {
        this.feedbackService = Objects.requireNonNull(
                feedbackService, "feedbackService não pode ser nulo");
    }

    @Override
    public Integer call() {
        if (!modelo) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(), "Informe a opção --modelo para gerar o arquivo modelo.");
        }
        LocalDate dataReferencia = dataTexto != null ? CliValidacoes.interpretarData(dataTexto) : null;
        CliValidacoes.validarCaminhoNaoEmBranco(caminhoAgenda, "--agenda");
        CliValidacoes.validarCaminhoNaoEmBranco(caminhoBacklog, "--backlog");

        ModeloFeedbackGerado resultado = feedbackService.gerarModelo(
                caminhoAgenda, caminhoBacklog, dataReferencia, sobrescrever);
        Semana semana = resultado.semanaPlanejada().semana();
        ResultadoAlocacao alocacao = resultado.resultadoAlocacao();

        PrintWriter out = spec.commandLine().getOut();
        out.printf(
                Locale.ROOT,
                "Modelo de feedback %04d-W%02d gerado em %s (%d item(ns)).%n",
                semana.ano(),
                semana.numeroIso(),
                resultado.arquivoSaida(),
                resultado.feedbackSemanal().itens().size());
        if (alocacao != null && !alocacao.naoAlocados().isEmpty()) {
            out.printf(
                    Locale.ROOT,
                    "Aviso: %d pedaço(s) de estudo não couberam nesta semana.%n",
                    alocacao.naoAlocados().size());
        }
        for (String aviso : resultado.semanaPlanejada().avisos()) {
            out.println("Aviso: " + aviso);
        }
        out.flush();
        return CommandLine.ExitCode.OK;
    }
}
