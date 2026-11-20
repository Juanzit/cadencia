package dev.cadencia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CadenciaApplicationTest {

    @Value("${cadencia.fuso}")
    private String fuso;

    @Test
    void contextoSobeComConfiguracaoPadrao() {
        assertThat(fuso).isEqualTo("America/Sao_Paulo");
    }
}
