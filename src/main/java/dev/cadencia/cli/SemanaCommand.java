package dev.cadencia.cli;

import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.Semana;
import dev.cadencia.exportacao.PlanoSemanalGerado;
import dev.cadencia.exportacao.PlanoSemanalService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

    private static final String PADRAO_DATA_ISO = "\\d{4}-\\d{2}-\\d{2}";

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

    @Spec
    private CommandSpec spec;

    public SemanaCommand(PlanoSemanalService planoSemanalService) {
        this.planoSemanalService = Objects.requireNonNull(
                planoSemanalService, "planoSemanalService não pode ser nulo");
    }

    @Override
    public Integer call() {
        LocalDate dataReferencia = dataTexto != null ? interpretarData(dataTexto) : null;
        if (caminhoAgenda != null && caminhoAgenda.toString().isBlank()) {
            throw new DadosInvalidosException(
                    "Caminho inválido para --agenda: informe o caminho de um arquivo YAML válido.");
        }

        PlanoSemanalGerado resultado = planoSemanalService.gerarEExportar(caminhoAgenda, dataReferencia);
        Semana semana = resultado.semanaPlanejada().semana();

        PrintWriter out = spec.commandLine().getOut();
        out.printf(
                Locale.ROOT,
                "Plano semanal %04d-W%02d gerado em %s (%d blocos livres).%n",
                semana.ano(),
                semana.numeroIso(),
                resultado.arquivoSaida(),
                semana.blocos().size());
        for (String aviso : resultado.semanaPlanejada().avisos()) {
            out.println("Aviso: " + aviso);
        }
        out.flush();
        return CommandLine.ExitCode.OK;
    }

    private static LocalDate interpretarData(String valor) {
        String limpo = valor.trim();
        if (!limpo.matches(PADRAO_DATA_ISO)) {
            throw new DadosInvalidosException(String.format(
                    "Data inválida para --data: \"%s\". Use o formato AAAA-MM-DD (ex.: 2026-11-23).",
                    valor));
        }
        try {
            return LocalDate.parse(limpo);
        } catch (DateTimeParseException ex) {
            throw new DadosInvalidosException(String.format(
                    "Data inválida para --data: \"%s\". Informe uma data existente no calendário no formato AAAA-MM-DD.",
                    valor), ex);
        }
    }
}
