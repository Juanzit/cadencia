package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgendaConfiguracaoTest {

    private static final Jornada JORNADA = new Jornada(LocalTime.of(7, 0), LocalTime.of(23, 0));
    private static final Margens MARGENS = new Margens(30, Map.of("EmpresaA", new MargemEmpresa(30, 0)));
    private static final Classificacao CLASSIFICACAO = new Classificacao(90, 25);
    private static final Compromisso COMPROMISSO = new Compromisso(
            DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofHours(2), TipoCompromisso.AULA, "EmpresaA", "CENTRO", "T01");
    private static final BlocoManual BLOCO_MANUAL = new BlocoManual(
            DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 30), TipoBloco.PROFUNDO, "Estudo");

    @Test
    void jornadaDeveValidarCamposObrigatoriosEOrdemDosHorarios() {
        assertThat(JORNADA.inicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(JORNADA.fim()).isEqualTo(LocalTime.of(23, 0));

        assertThatNullPointerException()
                .isThrownBy(() -> new Jornada(null, LocalTime.of(23, 0)));
        assertThatNullPointerException()
                .isThrownBy(() -> new Jornada(LocalTime.of(7, 0), null));

        assertThatThrownBy(() -> new Jornada(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Jornada(LocalTime.of(22, 0), LocalTime.of(6, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void margemEmpresaEMargensDevemValidarValoresEPreservarMapaImutavel() {
        assertThatThrownBy(() -> new MargemEmpresa(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MargemEmpresa(0, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Margens(-5, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException()
                .isThrownBy(() -> new Margens(10, null));

        Map<String, MargemEmpresa> chaveVazia = new HashMap<>();
        chaveVazia.put("  ", new MargemEmpresa(10, 0));
        assertThatThrownBy(() -> new Margens(10, chaveVazia))
                .isInstanceOf(IllegalArgumentException.class);

        Map<String, MargemEmpresa> chaveNula = new HashMap<>();
        chaveNula.put(null, new MargemEmpresa(10, 0));
        assertThatThrownBy(() -> new Margens(10, chaveNula))
                .isInstanceOf(IllegalArgumentException.class);

        Map<String, MargemEmpresa> valorNulo = new HashMap<>();
        valorNulo.put("EmpresaA", null);
        assertThatNullPointerException()
                .isThrownBy(() -> new Margens(10, valorNulo));

        Map<String, MargemEmpresa> original = new LinkedHashMap<>();
        original.put("EmpresaA", new MargemEmpresa(30, 0));
        original.put("EmpresaB", new MargemEmpresa(30, 15));

        Margens margens = new Margens(30, original);
        original.put("EmpresaC", new MargemEmpresa(0, 0));

        assertThat(margens.porEmpresa().keySet()).containsExactly("EmpresaA", "EmpresaB");
        assertThatThrownBy(() -> margens.porEmpresa().put("EmpresaD", new MargemEmpresa(0, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void classificacaoDeveExigirLimitesEstritamentePositivos() {
        assertThat(CLASSIFICACAO.profundoMinimoMin()).isEqualTo(90);
        assertThat(CLASSIFICACAO.interrompivelMinimoMin()).isEqualTo(25);

        assertThatThrownBy(() -> new Classificacao(0, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Classificacao(-10, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Classificacao(90, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Classificacao(90, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blocoManualDeveValidarObrigatoriosEIntervalo() {
        BlocoManual semDescricao = new BlocoManual(
                DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), TipoBloco.VIGILANCIA, null);
        assertThat(semDescricao.descricao()).isNull();

        assertThatNullPointerException()
                .isThrownBy(() -> new BlocoManual(null, LocalTime.of(8, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new BlocoManual(DayOfWeek.MONDAY, null, LocalTime.of(9, 0), TipoBloco.PROFUNDO, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(8, 0), null, TipoBloco.PROFUNDO, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), null, null));

        assertThatThrownBy(() -> new BlocoManual(
                        DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlocoManual(
                        DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(1, 0), TipoBloco.PROFUNDO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agendaConfiguracaoDeveValidarCamposEProtegerListas() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(null, MARGENS, CLASSIFICACAO, List.of(), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(JORNADA, null, CLASSIFICACAO, List.of(), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(JORNADA, MARGENS, null, List.of(), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(JORNADA, MARGENS, CLASSIFICACAO, null, List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(JORNADA, MARGENS, CLASSIFICACAO, List.of(), null));

        List<Compromisso> compromissosComNulo = Arrays.asList(COMPROMISSO, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(JORNADA, MARGENS, CLASSIFICACAO, compromissosComNulo, List.of()));

        List<BlocoManual> blocosComNulo = Arrays.asList(BLOCO_MANUAL, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaConfiguracao(JORNADA, MARGENS, CLASSIFICACAO, List.of(), blocosComNulo));

        List<Compromisso> compromissosMutaveis = new ArrayList<>(List.of(COMPROMISSO));
        List<BlocoManual> blocosMutaveis = new ArrayList<>(List.of(BLOCO_MANUAL));
        AgendaConfiguracao config = new AgendaConfiguracao(
                JORNADA, MARGENS, CLASSIFICACAO, compromissosMutaveis, blocosMutaveis);

        compromissosMutaveis.clear();
        blocosMutaveis.clear();

        assertThat(config.compromissos()).containsExactly(COMPROMISSO);
        assertThat(config.blocosManuais()).containsExactly(BLOCO_MANUAL);

        assertThatThrownBy(() -> config.compromissos().add(COMPROMISSO))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> config.blocosManuais().add(BLOCO_MANUAL))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
