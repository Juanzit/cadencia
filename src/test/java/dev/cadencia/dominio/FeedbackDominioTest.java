package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedbackDominioTest {

    @Test
    void statusBlocoDeveExporTresEstadosEPesosEstatisticosDeterministicos() {
        assertThat(StatusBloco.values()).containsExactly(
                StatusBloco.CONCLUIDO,
                StatusBloco.INTERROMPIDO,
                StatusBloco.NAO_ACONTECEU);
        assertThat(StatusBloco.CONCLUIDO.peso()).isEqualTo(1.0);
        assertThat(StatusBloco.INTERROMPIDO.peso()).isEqualTo(0.5);
        assertThat(StatusBloco.NAO_ACONTECEU.peso()).isEqualTo(0.0);
    }

    @Test
    void periodoDiaDeveClassificarCorretamenteNosLimitesExatos() {
        assertThat(PeriodoDia.values()).containsExactly(
                PeriodoDia.MANHA,
                PeriodoDia.TARDE,
                PeriodoDia.NOITE);
        assertThat(PeriodoDia.de(LocalTime.of(0, 0))).isEqualTo(PeriodoDia.MANHA);
        assertThat(PeriodoDia.de(LocalTime.of(11, 59))).isEqualTo(PeriodoDia.MANHA);
        assertThat(PeriodoDia.de(LocalTime.of(12, 0))).isEqualTo(PeriodoDia.TARDE);
        assertThat(PeriodoDia.de(LocalTime.of(17, 59))).isEqualTo(PeriodoDia.TARDE);
        assertThat(PeriodoDia.de(LocalTime.of(18, 0))).isEqualTo(PeriodoDia.NOITE);
        assertThat(PeriodoDia.de(LocalTime.of(23, 59))).isEqualTo(PeriodoDia.NOITE);

        assertThatNullPointerException().isThrownBy(() -> PeriodoDia.de(null));
    }

    @Test
    void faixaHorarioDeveValidarCamposOBriatoriosEDerivarPorHorario() {
        FaixaHorario faixa = FaixaHorario.de(DayOfWeek.TUESDAY, LocalTime.of(15, 0));

        assertThat(faixa).isEqualTo(new FaixaHorario(DayOfWeek.TUESDAY, PeriodoDia.TARDE));
        assertThat(faixa.diaSemana()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(faixa.periodo()).isEqualTo(PeriodoDia.TARDE);

        assertThatNullPointerException()
                .isThrownBy(() -> new FaixaHorario(null, PeriodoDia.MANHA));
        assertThatNullPointerException()
                .isThrownBy(() -> new FaixaHorario(DayOfWeek.MONDAY, null));
        assertThatNullPointerException()
                .isThrownBy(() -> FaixaHorario.de(null, LocalTime.of(9, 0)));
        assertThatNullPointerException()
                .isThrownBy(() -> FaixaHorario.de(DayOfWeek.MONDAY, null));
    }

    @Test
    void itemFeedbackDeveNormalizarCamposEPermitirFimENotaRetomadaOpcionais() {
        ItemFeedback comIntervaloENota = new ItemFeedback(
                DayOfWeek.WEDNESDAY,
                LocalTime.of(19, 30),
                LocalTime.of(20, 0),
                "  Java Collections (2/6)  ",
                StatusBloco.INTERROMPIDO,
                "  parei no exercício 4  ");

        assertThat(comIntervaloENota.titulo()).isEqualTo("Java Collections (2/6)");
        assertThat(comIntervaloENota.notaRetomada()).isEqualTo("parei no exercício 4");
        assertThat(comIntervaloENota.possuiNotaRetomada()).isTrue();
        assertThat(comIntervaloENota.faixaHorario())
                .isEqualTo(new FaixaHorario(DayOfWeek.WEDNESDAY, PeriodoDia.NOITE));

        ItemFeedback semFimComNotaEmBranco = new ItemFeedback(
                DayOfWeek.MONDAY,
                LocalTime.of(7, 0),
                null,
                "Spring Boot (1/5)",
                StatusBloco.CONCLUIDO,
                "   ");
        assertThat(semFimComNotaEmBranco.fim()).isNull();
        assertThat(semFimComNotaEmBranco.notaRetomada()).isNull();
        assertThat(semFimComNotaEmBranco.possuiNotaRetomada()).isFalse();
        assertThat(semFimComNotaEmBranco.faixaHorario())
                .isEqualTo(new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA));

        ItemFeedback semFimComNotaNula = new ItemFeedback(
                DayOfWeek.THURSDAY,
                LocalTime.of(14, 0),
                null,
                "SQL (1/3)",
                StatusBloco.NAO_ACONTECEU,
                null);
        assertThat(semFimComNotaNula.notaRetomada()).isNull();
        assertThat(semFimComNotaNula.possuiNotaRetomada()).isFalse();
    }

    @Test
    void itemFeedbackDeveValidarInvariantesObrigatoriosEIntervalo() {
        LocalTime oito = LocalTime.of(8, 0);
        LocalTime nove = LocalTime.of(9, 0);

        assertThatNullPointerException()
                .isThrownBy(() -> new ItemFeedback(null, oito, nove, "Estudo", StatusBloco.CONCLUIDO, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new ItemFeedback(DayOfWeek.MONDAY, null, nove, "Estudo", StatusBloco.CONCLUIDO, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new ItemFeedback(DayOfWeek.MONDAY, oito, nove, null, StatusBloco.CONCLUIDO, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new ItemFeedback(DayOfWeek.MONDAY, oito, nove, "Estudo", null, null));

        assertThatThrownBy(() -> new ItemFeedback(DayOfWeek.MONDAY, oito, nove, "   ", StatusBloco.CONCLUIDO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemFeedback(DayOfWeek.MONDAY, oito, oito, "Estudo", StatusBloco.CONCLUIDO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemFeedback(
                        DayOfWeek.MONDAY, nove, oito, "Estudo", StatusBloco.CONCLUIDO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void feedbackSemanalDeveValidarLimitesEProtegerListasComCopiaDefensivaImutavel() {
        ItemFeedback item = new ItemFeedback(
                DayOfWeek.FRIDAY,
                LocalTime.of(10, 0),
                LocalTime.of(10, 25),
                "JPA (1/4)",
                StatusBloco.CONCLUIDO,
                null);
        List<ItemFeedback> itensMutaveis = new ArrayList<>(List.of(item));
        List<String> notasMutaveis = new ArrayList<>(List.of("Semana produtiva."));

        FeedbackSemanal inicioLimites = new FeedbackSemanal(2000, 1, itensMutaveis, notasMutaveis);
        FeedbackSemanal fimLimites = new FeedbackSemanal(2026, 53, List.of(), List.of());

        itensMutaveis.clear();
        notasMutaveis.clear();

        assertThat(inicioLimites.ano()).isEqualTo(2000);
        assertThat(inicioLimites.numeroIso()).isEqualTo(1);
        assertThat(inicioLimites.itens()).containsExactly(item);
        assertThat(inicioLimites.notasGerais()).containsExactly("Semana produtiva.");
        assertThat(fimLimites.numeroIso()).isEqualTo(53);

        assertThatThrownBy(() -> inicioLimites.itens().add(item))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> inicioLimites.notasGerais().add("Outra nota"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> new FeedbackSemanal(1999, 1, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedbackSemanal(2026, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedbackSemanal(2026, 54, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatNullPointerException()
                .isThrownBy(() -> new FeedbackSemanal(2026, 48, null, List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new FeedbackSemanal(2026, 48, List.of(), null));

        List<ItemFeedback> itensComNulo = Arrays.asList(item, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new FeedbackSemanal(2026, 48, itensComNulo, List.of()));

        List<String> notasComNulo = Arrays.asList("Nota", null);
        assertThatNullPointerException()
                .isThrownBy(() -> new FeedbackSemanal(2026, 48, List.of(), notasComNulo));
    }
}
