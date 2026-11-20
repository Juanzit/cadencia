package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BlocoTest {

    private static final LocalDate DATA = LocalDate.of(2026, 11, 23);

    @Test
    void deveCriarBlocoValidoEAceitarDescricaoNula() {
        Bloco bloco = new Bloco(
                DATA,
                LocalTime.of(20, 0),
                LocalTime.of(22, 0),
                TipoBloco.PROFUNDO,
                "Java Collections");

        assertThat(bloco.data()).isEqualTo(DATA);
        assertThat(bloco.inicio()).isEqualTo(LocalTime.of(20, 0));
        assertThat(bloco.fim()).isEqualTo(LocalTime.of(22, 0));
        assertThat(bloco.tipo()).isEqualTo(TipoBloco.PROFUNDO);
        assertThat(bloco.descricao()).isEqualTo("Java Collections");

        Bloco semDescricao = new Bloco(
                DATA,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                TipoBloco.INTERROMPIVEL,
                null);

        assertThat(semDescricao.descricao()).isNull();
    }

    @Test
    void deveRejeitarCamposObrigatoriosNulos() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Bloco(null, LocalTime.of(9, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null));

        assertThatNullPointerException()
                .isThrownBy(() -> new Bloco(DATA, null, LocalTime.of(10, 0), TipoBloco.PROFUNDO, null));

        assertThatNullPointerException()
                .isThrownBy(() -> new Bloco(DATA, LocalTime.of(9, 0), null, TipoBloco.PROFUNDO, null));

        assertThatNullPointerException()
                .isThrownBy(() -> new Bloco(DATA, LocalTime.of(9, 0), LocalTime.of(10, 0), null, null));
    }

    @Test
    void deveRejeitarFimIgualOuAnteriorAoInicio() {
        LocalTime nove = LocalTime.of(9, 0);
        assertThatThrownBy(() -> new Bloco(DATA, nove, nove, TipoBloco.VIGILANCIA, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Bloco(
                        DATA, LocalTime.of(23, 0), LocalTime.of(1, 0), TipoBloco.PROFUNDO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
