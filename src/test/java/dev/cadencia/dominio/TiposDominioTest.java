package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TiposDominioTest {

    @Test
    void tipoBlocoDeveConterExatamenteOsTresValoresDefinidos() {
        assertThat(TipoBloco.values()).containsExactly(
                TipoBloco.PROFUNDO,
                TipoBloco.INTERROMPIVEL,
                TipoBloco.VIGILANCIA);
    }

    @Test
    void tipoCompromissoDeveConterExatamenteOsSeisValoresDefinidos() {
        assertThat(TipoCompromisso.values()).containsExactly(
                TipoCompromisso.AULA,
                TipoCompromisso.REUNIAO,
                TipoCompromisso.ALMOCO,
                TipoCompromisso.DESLOCAMENTO,
                TipoCompromisso.TREINO,
                TipoCompromisso.PLANTAO);
    }
}
