package dev.cadencia.cli;

import dev.cadencia.agenda.DadosInvalidosException;
import java.io.PrintWriter;
import java.util.Objects;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Executor da CLI no ciclo de vida do Spring Boot: delega para o Picocli via {@link CommandLine.IFactory},
 * traduz exceções de dados/execução para o contrato de códigos de saída e expõe o código final via
 * {@link ExitCodeGenerator}.
 */
@Component
public class PicocliCommandRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final int CODIGO_DADOS_INVALIDOS = 2;

    private final CommandLine.IFactory factory;
    private int exitCode;

    public PicocliCommandRunner(CommandLine.IFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory não pode ser nula");
    }

    @Override
    public void run(String... args) {
        this.exitCode = criarCommandLine().execute(args);
    }

    /**
     * Executa o comando com fluxos de saída configuráveis, útil em testes sem redirecionamento
     * global de {@code System.out}/{@code System.err}.
     */
    public int executar(PrintWriter out, PrintWriter err, String... args) {
        Objects.requireNonNull(out, "out não pode ser nulo");
        Objects.requireNonNull(err, "err não pode ser nulo");
        CommandLine commandLine = criarCommandLine();
        commandLine.setOut(out);
        commandLine.setErr(err);
        this.exitCode = commandLine.execute(args);
        return this.exitCode;
    }

    @Override
    public int getExitCode() {
        return this.exitCode;
    }

    private CommandLine criarCommandLine() {
        CommandLine commandLine = new CommandLine(CadenciaCommand.class, factory);
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> tratarErroExecucao(ex, cmd, parseResult));
        commandLine.setParameterExceptionHandler((ex, args) -> tratarErroParametro(ex));
        return commandLine;
    }

    private static int tratarErroExecucao(
            Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
        PrintWriter err = cmd.getErr();
        if (ex instanceof DadosInvalidosException) {
            err.println(ex.getMessage());
            err.flush();
            return CODIGO_DADOS_INVALIDOS;
        }

        String detalhes = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : ex.getClass().getSimpleName();
        err.println("Erro inesperado: " + detalhes);
        if (verboseAtivo(cmd, parseResult)) {
            ex.printStackTrace(err);
        } else {
            err.println("Execute novamente com --verbose para ver os detalhes técnicos.");
        }
        err.flush();
        return CommandLine.ExitCode.SOFTWARE;
    }

    private static int tratarErroParametro(CommandLine.ParameterException ex) {
        CommandLine cmd = ex.getCommandLine();
        PrintWriter err = cmd.getErr();
        err.println("Argumentos inválidos: " + ex.getMessage());
        cmd.usage(err);
        err.flush();
        return CommandLine.ExitCode.USAGE;
    }

    private static boolean verboseAtivo(CommandLine cmd, CommandLine.ParseResult parseResult) {
        CommandLine.ParseResult raiz = cmd.getCommandSpec().root().commandLine().getParseResult();
        for (CommandLine.ParseResult atual = raiz != null ? raiz : parseResult;
                atual != null;
                atual = atual.subcommand()) {
            if (atual.hasMatchedOption("--verbose")) {
                return true;
            }
        }
        return false;
    }
}
