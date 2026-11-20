package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompromissoTest {

    @Test
    void deveCriarCompromissoValidoEAcceptarCamposOpcionaisNulos() {
        Compromisso completo = new Compromisso(
                DayOfWeek.WEDNESDAY,
                LocalTime.of(14, 0),
                Duration.ofMinutes(90),
                TipoCompromisso.AULA,
                "EmpresaA",
                "UNIDADE_CENTRO",
                "Turma T01");

        assertThat(completo.diaSemana()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(completo.inicio()).isEqualTo(LocalTime.of(14, 0));
        assertThat(completo.duracao()).isEqualTo(Duration.ofMinutes(90));
        assertThat(completo.fim()).isEqualTo(LocalTime.of(15, 30));

        Compromisso semOpcionais = new Compromisso(
                DayOfWeek.FRIDAY,
                LocalTime.of(18, 0),
                Duration.ofHours(1),
                TipoCompromisso.TREINO,
                null,
                null,
                null);

        assertThat(semOpcionais.empresa()).isNull();
        assertThat(semOpcionais.local()).isNull();
        assertThat(semOpcionais.titulo()).isNull();
    }

    @Test
    void deveRejeitarCamposObrigatoriosNulos() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Compromisso(
                        null, LocalTime.of(9, 0), Duration.ofHours(1), TipoCompromisso.AULA, null, null, null));

        assertThatNullPointerException()
                .isThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, null, Duration.ofHours(1), TipoCompromisso.AULA, null, null, null));

        assertThatNullPointerException()
                .isThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), null, TipoCompromisso.AULA, null, null, null));

        assertThatNullPointerException()
                .isThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofHours(1), null, null, null, null));
    }

    @Test
    void deveRejeitarDuracaoZeroNegativaOuMaiorIgual24Horas() {
        assertThatThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ZERO, TipoCompromisso.REUNIAO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofMinutes(-15), TipoCompromisso.REUNIAO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofHours(24), TipoCompromisso.PLANTAO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Compromisso(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofHours(25), TipoCompromisso.PLANTAO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void casoANaoCruzaMeiaNoite() {
        Compromisso compromisso = new Compromisso(
                DayOfWeek.MONDAY,
                LocalTime.of(10, 0),
                Duration.ofHours(2),
                TipoCompromisso.AULA,
                "EmpresaA",
                "UNIDADE_CENTRO",
                "Aula matinal");

        assertThat(compromisso.fim()).isEqualTo(LocalTime.of(12, 0));
        assertThat(compromisso.cruzaMeiaNoite()).isFalse();

        List<Compromisso> fragmentos = compromisso.fatiarPorDia();
        assertThat(fragmentos).containsExactly(compromisso);
        assertThatThrownBy(() -> fragmentos.add(compromisso))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void casoBCruzaMeiaNoite() {
        Compromisso compromisso = new Compromisso(
                DayOfWeek.MONDAY,
                LocalTime.of(23, 0),
                Duration.ofHours(2),
                TipoCompromisso.PLANTAO,
                "EmpresaB",
                "CASA",
                "Plantão noturno");

        assertThat(compromisso.fim()).isEqualTo(LocalTime.of(1, 0));
        assertThat(compromisso.cruzaMeiaNoite()).isTrue();

        List<Compromisso> fragmentos = compromisso.fatiarPorDia();
        assertThat(fragmentos).hasSize(2);

        Compromisso primeiro = fragmentos.get(0);
        assertThat(primeiro.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(primeiro.inicio()).isEqualTo(LocalTime.of(23, 0));
        assertThat(primeiro.duracao()).isEqualTo(Duration.ofHours(1));
        assertThat(primeiro.fim()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(primeiro.cruzaMeiaNoite()).isFalse();
        assertThat(primeiro.tipo()).isEqualTo(TipoCompromisso.PLANTAO);
        assertThat(primeiro.empresa()).isEqualTo("EmpresaB");
        assertThat(primeiro.local()).isEqualTo("CASA");
        assertThat(primeiro.titulo()).isEqualTo("Plantão noturno");

        Compromisso segundo = fragmentos.get(1);
        assertThat(segundo.diaSemana()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(segundo.inicio()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(segundo.duracao()).isEqualTo(Duration.ofHours(1));
        assertThat(segundo.fim()).isEqualTo(LocalTime.of(1, 0));
        assertThat(segundo.cruzaMeiaNoite()).isFalse();
        assertThat(segundo.tipo()).isEqualTo(TipoCompromisso.PLANTAO);
        assertThat(segundo.empresa()).isEqualTo("EmpresaB");
        assertThat(segundo.local()).isEqualTo("CASA");
        assertThat(segundo.titulo()).isEqualTo("Plantão noturno");

        Duration soma = primeiro.duracao().plus(segundo.duracao());
        assertThat(soma).isEqualTo(compromisso.duracao());

        assertThatThrownBy(() -> fragmentos.remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void casoCTerminaExatamenteAMeiaNoite() {
        Compromisso compromisso = new Compromisso(
                DayOfWeek.MONDAY,
                LocalTime.of(23, 0),
                Duration.ofHours(1),
                TipoCompromisso.PLANTAO,
                "EmpresaB",
                "CASA",
                "Plantão até 00h");

        assertThat(compromisso.fim()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(compromisso.cruzaMeiaNoite()).isFalse();

        List<Compromisso> fragmentos = compromisso.fatiarPorDia();
        assertThat(fragmentos).containsExactly(compromisso);
    }

    @Test
    void casoDDomingoParaSegundaFeira() {
        Compromisso compromisso = new Compromisso(
                DayOfWeek.SUNDAY,
                LocalTime.of(23, 0),
                Duration.ofHours(2),
                TipoCompromisso.PLANTAO,
                "EmpresaB",
                "CASA",
                "Plantão de domingo");

        assertThat(compromisso.fim()).isEqualTo(LocalTime.of(1, 0));
        assertThat(compromisso.cruzaMeiaNoite()).isTrue();

        List<Compromisso> fragmentos = compromisso.fatiarPorDia();
        assertThat(fragmentos).hasSize(2);

        Compromisso domingo = fragmentos.get(0);
        assertThat(domingo.diaSemana()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(domingo.inicio()).isEqualTo(LocalTime.of(23, 0));
        assertThat(domingo.duracao()).isEqualTo(Duration.ofHours(1));

        Compromisso segunda = fragmentos.get(1);
        assertThat(segunda.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(segunda.inicio()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(segunda.duracao()).isEqualTo(Duration.ofHours(1));

        assertThat(domingo.duracao().plus(segunda.duracao())).isEqualTo(Duration.ofHours(2));
    }
}
