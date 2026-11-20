package dev.cadencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap mínimo do Cadência.
 *
 * <p>Sem regras de negócio nem comandos: a integração com Picocli entra nas próximas tarefas.
 */
@SpringBootApplication
public class CadenciaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CadenciaApplication.class, args);
    }
}
