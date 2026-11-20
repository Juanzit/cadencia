package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanaTest {

    private static final Compromisso COMPROMISSO = new Compromisso(
            DayOfWeek.MONDAY,
            LocalTime.of(9, 0),
            Duration.ofHours(2),
            TipoCompromisso.AULA,
            "EmpresaA",
            "UNIDADE_CENTRO",
            "Turma T01");

    private static final Bloco BLOCO = new Bloco(
            LocalDate.of(2026, 11, 23),
            LocalTime.of(20, 0),
            LocalTime.of(22, 0),
            TipoBloco.PROFUNDO,
            "Estudo");

    @Test
    void deveCriarSemanaValidaNosLimitesIso() {
        Semana primeira = new Semana(2026, 1, List.of(COMPROMISSO), List.of(BLOCO));
        Semana ultima = new Semana(2026, 53, List.of(), List.of());

        assertThat(primeira.ano()).isEqualTo(2026);
        assertThat(primeira.numeroIso()).isEqualTo(1);
        assertThat(primeira.compromissos()).containsExactly(COMPROMISSO);
        assertThat(primeira.blocos()).containsExactly(BLOCO);
        assertThat(ultima.numeroIso()).isEqualTo(53);
    }

    @Test
    void deveRejeitarNumeroIsoForaDoIntervalo() {
        assertThatThrownBy(() -> new Semana(2026, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Semana(2026, 54, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarListasNulasOuComElementosNulos() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Semana(2026, 48, null, List.of()));

        assertThatNullPointerException()
                .isThrownBy(() -> new Semana(2026, 48, List.of(), null));

        List<Compromisso> compromissosComNulo = Arrays.asList(COMPROMISSO, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new Semana(2026, 48, compromissosComNulo, List.of()));

        List<Bloco> blocosComNulo = Arrays.asList(BLOCO, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new Semana(2026, 48, List.of(), blocosComNulo));
    }

    @Test
    void deveFazerCopiaDefensivaERetornarListasImutaveis() {
        List<Compromisso> compromissosOriginais = new ArrayList<>();
        compromissosOriginais.add(COMPROMISSO);

        List<Bloco> blocosOriginais = new ArrayList<>();
        blocosOriginais.add(BLOCO);

        Semana semana = new Semana(2026, 48, compromissosOriginais, blocosOriginais);

        compromissosOriginais.clear();
        blocosOriginais.clear();

        assertThat(semana.compromissos()).containsExactly(COMPROMISSO);
        assertThat(semana.blocos()).containsExactly(BLOCO);

        assertThatThrownBy(() -> semana.compromissos().add(COMPROMISSO))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> semana.compromissos().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> semana.blocos().add(BLOCO))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> semana.blocos().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
