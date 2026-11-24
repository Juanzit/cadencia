package dev.cadencia.cli;

import dev.cadencia.dominio.Semana;
import dev.cadencia.exportacao.PlanoSemanalGerado;
import dev.cadencia.exportacao.PlanoSemanalService;
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
 * Subcomando {@code cadencia semana} que gera e salva o plano semanal Markdown em
 * {@code <diretorioDados>/semana/AAAA-Wnn.md}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(
        name = "semana",
        mixinStandardHelpOptions = true,
        description = "Gera o plano semanal Markdown da semana ISO de referência.")
public class SemanaCommand implements Callable<Integer> {

    private final PlanoSemanalService planoSemanalService;

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

    @Spec
    private CommandSpec spec;

    public SemanaCommand(PlanoSemanalService planoSemanalService) {
        this.planoSemanalService = Objects.requireNonNull(
                planoSemanalService, "planoSemanalService não pode ser nulo");
    }

    @Override
    public Integer call() {
        LocalDate dataReferencia = dataTexto != null ? CliValidacoes.interpretarData(dataTexto) : null;
        CliValidacoes.validarCaminhoNaoEmBranco(caminhoAgenda, "--agenda");
        CliValidacoes.validarCaminhoNaoEmBranco(caminhoBacklog, "--backlog");

        PlanoSemanalGerado resultado = planoSemanalService.gerarEExportar(
                caminhoAgenda, caminhoBacklog, dataReferencia);
        Semana semana = resultado.semanaPlanejada().semana();
        ResultadoAlocacao alocacao = resultado.resultadoAlocacao();

        PrintWriter out = spec.commandLine().getOut();
        if (alocacao == null) {
            out.printf(
                    Locale.ROOT,
                    "Plano semanal %04d-W%02d gerado em %s (%d blocos livres).%n",
                    semana.ano(),
                    semana.numeroIso(),
                    resultado.arquivoSaida(),
                    semana.blocos().size());
        } else {
            out.printf(
                    Locale.ROOT,
                    "Plano semanal %04d-W%02d gerado em %s (%d blocos livres, %d pedaço(s) alocado(s)).%n",
                    semana.ano(),
                    semana.numeroIso(),
                    resultado.arquivoSaida(),
                    semana.blocos().size(),
                    alocacao.alocados().size());
            if (!alocacao.naoAlocados().isEmpty()) {
                out.printf(
                        Locale.ROOT,
                        "Aviso: %d pedaço(s) de estudo não couberam nesta semana.%n",
                        alocacao.naoAlocados().size());
            }
        }
        for (String aviso : resultado.semanaPlanejada().avisos()) {
            out.println("Aviso: " + aviso);
        }
        out.flush();
        return CommandLine.ExitCode.OK;
    }
}
