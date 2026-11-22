package dev.cadencia.cli;

import java.util.concurrent.Callable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

/**
 * Comando raiz da CLI {@code cadencia}.
 *
 * <p>Quando executado sem subcomando (como na inicialização padrão de testes Spring sem argumentos),
 * exibe apenas a ajuda e retorna código {@code 0} sem efeitos colaterais em disco.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(
        name = "cadencia",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Planejador semanal local para organizar estudos e rotina.",
        subcommands = {SemanaCommand.class})
public class CadenciaCommand implements Callable<Integer> {

    @Option(
            names = "--verbose",
            scope = ScopeType.INHERIT,
            description = "Exibe o stack trace completo em erros inesperados.")
    private boolean verbose;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        spec.commandLine().getOut().flush();
        return CommandLine.ExitCode.OK;
    }

    boolean isVerbose() {
        return verbose;
    }
}
