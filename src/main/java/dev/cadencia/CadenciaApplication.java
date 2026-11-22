package dev.cadencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Ponto de entrada Spring Boot da aplicação Cadência.
 *
 * <p>Executa a CLI via {@link org.springframework.boot.CommandLineRunner}, obtém o código de saída
 * via {@link SpringApplication#exit(org.springframework.context.ApplicationContext, org.springframework.boot.ExitCodeGenerator...)}
 * e só então encerra o processo no método {@link #main(String[])}.
 */
@SpringBootApplication
public class CadenciaApplication {

    public static void main(String[] args) {
        System.exit(executar(args));
    }

    static int executar(String... args) {
        ConfigurableApplicationContext context = SpringApplication.run(CadenciaApplication.class, args);
        return SpringApplication.exit(context);
    }
}
