package dev.cadencia.cli;

import java.util.Objects;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Fábrica do Picocli integrada ao {@link ApplicationContext} do Spring para resolver comandos e suas
 * dependências gerenciadas por injeção.
 */
@Component
public class PicocliSpringFactory implements CommandLine.IFactory {

    private final ApplicationContext applicationContext;
    private final CommandLine.IFactory fallbackFactory = CommandLine.defaultFactory();

    public PicocliSpringFactory(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(
                applicationContext, "applicationContext não pode ser nulo");
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        try {
            return applicationContext.getBean(cls);
        } catch (NoSuchBeanDefinitionException ex) {
            return fallbackFactory.create(cls);
        }
    }
}
