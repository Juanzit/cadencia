package dev.cadencia.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TurmaTest {

    @Test
    void deveNormalizarCamposECalcularFimNoDomingoETransicaoNaSegunda() {
        Turma turma = new Turma(
                "  T-101  ",
                "  EmpresaA ",
                "  Java Core  ",
                LocalDate.of(2026, 11, 2),
                4);

        assertEquals("T-101", turma.codigo());
        assertEquals("EmpresaA", turma.empresa());
        assertEquals("Java Core", turma.moduloAtual());
        assertEquals(LocalDate.of(2026, 11, 29), turma.dataFimModulo());
        assertEquals(LocalDate.of(2026, 11, 30), turma.dataPrevistaTransicao());

        assertEquals(new Semana(2026, 48, List.of(), List.of()), turma.semanaEncerramento());
        assertEquals(new Semana(2026, 49, List.of(), List.of()), turma.semanaTransicaoPrevista());

        PrevisaoTransicao encerramento = turma.eventoNaSemana(new Semana(2026, 48, List.of(), List.of()))
                .orElseThrow();
        assertEquals(TipoEventoTurma.ENCERRAMENTO_MODULO, encerramento.tipo());
        assertEquals(2026, encerramento.anoIso());
        assertEquals(48, encerramento.semanaIso());
        assertEquals(LocalDate.of(2026, 11, 29), encerramento.dataReferencia());
        assertEquals(turma, encerramento.turma());

        PrevisaoTransicao transicao = turma.eventoNaSemana(new Semana(2026, 49, List.of(), List.of()))
                .orElseThrow();
        assertEquals(TipoEventoTurma.TRANSICAO_PREVISTA, transicao.tipo());
        assertEquals(2026, transicao.anoIso());
        assertEquals(49, transicao.semanaIso());
        assertEquals(LocalDate.of(2026, 11, 30), transicao.dataReferencia());
    }

    @Test
    void deveSepararEncerramentoETransicaoComDuracaoMinimaQuandoIniciaNaSegunda() {
        Turma turma = new Turma(
                "T-102",
                "EmpresaA",
                "Spring Boot",
                LocalDate.of(2026, 11, 23),
                1);

        assertEquals(LocalDate.of(2026, 11, 29), turma.dataFimModulo());
        assertEquals(LocalDate.of(2026, 11, 30), turma.dataPrevistaTransicao());
        assertEquals(TipoEventoTurma.ENCERRAMENTO_MODULO,
                turma.eventoNaSemana(new Semana(2026, 48, List.of(), List.of())).orElseThrow().tipo());
        assertEquals(TipoEventoTurma.TRANSICAO_PREVISTA,
                turma.eventoNaSemana(new Semana(2026, 49, List.of(), List.of())).orElseThrow().tipo());
    }

    @Test
    void devePriorizarEncerramentoQuandoEncerramentoETransicaoCoincidemNaMesmaSemanaIso() {
        Turma turma = new Turma(
                "T-103",
                "EmpresaB",
                "Arquitetura",
                LocalDate.of(2026, 11, 24),
                1);

        assertEquals(LocalDate.of(2026, 11, 30), turma.dataFimModulo());
        assertEquals(LocalDate.of(2026, 12, 1), turma.dataPrevistaTransicao());
        assertEquals(new Semana(2026, 49, List.of(), List.of()), turma.semanaEncerramento());
        assertEquals(new Semana(2026, 49, List.of(), List.of()), turma.semanaTransicaoPrevista());

        PrevisaoTransicao evento = turma.eventoNaSemana(new Semana(2026, 49, List.of(), List.of()))
                .orElseThrow();
        assertEquals(TipoEventoTurma.ENCERRAMENTO_MODULO, evento.tipo());
        assertEquals(LocalDate.of(2026, 11, 30), evento.dataReferencia());
        assertTrue(turma.eventoNaSemana(new Semana(2026, 48, List.of(), List.of())).isEmpty());
    }

    @Test
    void deveCalcularViradasDeAnoIso2025Para2026E2026W53Para2027W01() {
        Turma virada2025 = new Turma(
                "T-201",
                "EmpresaA",
                "POO",
                LocalDate.of(2025, 12, 22),
                1);
        assertEquals(LocalDate.of(2025, 12, 28), virada2025.dataFimModulo());
        assertEquals(LocalDate.of(2025, 12, 29), virada2025.dataPrevistaTransicao());
        assertEquals(new Semana(2025, 52, List.of(), List.of()), virada2025.semanaEncerramento());
        assertEquals(new Semana(2026, 1, List.of(), List.of()), virada2025.semanaTransicaoPrevista());
        assertEquals(TipoEventoTurma.TRANSICAO_PREVISTA,
                virada2025.eventoNaSemana(new Semana(2026, 1, List.of(), List.of())).orElseThrow().tipo());

        Turma viradaW53 = new Turma(
                "T-202",
                "EmpresaA",
                "SQL",
                LocalDate.of(2026, 12, 7),
                4);
        assertEquals(LocalDate.of(2027, 1, 3), viradaW53.dataFimModulo());
        assertEquals(LocalDate.of(2027, 1, 4), viradaW53.dataPrevistaTransicao());
        assertEquals(new Semana(2026, 53, List.of(), List.of()), viradaW53.semanaEncerramento());
        assertEquals(new Semana(2027, 1, List.of(), List.of()), viradaW53.semanaTransicaoPrevista());
        assertEquals(TipoEventoTurma.ENCERRAMENTO_MODULO,
                viradaW53.eventoNaSemana(new Semana(2026, 53, List.of(), List.of())).orElseThrow().tipo());
        assertEquals(TipoEventoTurma.TRANSICAO_PREVISTA,
                viradaW53.eventoNaSemana(new Semana(2027, 1, List.of(), List.of())).orElseThrow().tipo());
    }

    @Test
    void deveRetornarVazioQuandoAnoOuSemanaNaoCorresponderAosEventos() {
        Turma turma = new Turma(
                "T-301",
                "EmpresaA",
                "Testes",
                LocalDate.of(2026, 11, 2),
                4);

        assertEquals(Optional.empty(), turma.eventoNaSemana(new Semana(2026, 47, List.of(), List.of())));
        assertEquals(Optional.empty(), turma.eventoNaSemana(new Semana(2025, 48, List.of(), List.of())));
        assertEquals(Optional.empty(), turma.eventoNaSemana(new Semana(2027, 49, List.of(), List.of())));
    }

    @Test
    void deveValidarInvariantesDeTurmaEEventoNaSemana() {
        LocalDate inicio = LocalDate.of(2026, 11, 2);

        assertThrows(NullPointerException.class, () -> new Turma(null, "Empresa", "Módulo", inicio, 4));
        assertThrows(NullPointerException.class, () -> new Turma("T-1", null, "Módulo", inicio, 4));
        assertThrows(NullPointerException.class, () -> new Turma("T-1", "Empresa", null, inicio, 4));
        assertThrows(NullPointerException.class, () -> new Turma("T-1", "Empresa", "Módulo", null, 4));

        assertThrows(IllegalArgumentException.class, () -> new Turma("  ", "Empresa", "Módulo", inicio, 4));
        assertThrows(IllegalArgumentException.class, () -> new Turma("T-1", "\t", "Módulo", inicio, 4));
        assertThrows(IllegalArgumentException.class, () -> new Turma("T-1", "Empresa", "", inicio, 4));
        assertThrows(IllegalArgumentException.class, () -> new Turma("T-1", "Empresa", "Módulo", inicio, 0));
        assertThrows(IllegalArgumentException.class, () -> new Turma("T-1", "Empresa", "Módulo", inicio, -2));

        Turma turma = new Turma("T-1", "Empresa", "Módulo", inicio, 4);
        assertThrows(NullPointerException.class, () -> turma.eventoNaSemana(null));
    }

    @Test
    void deveValidarInvariantesDePrevisaoTransicao() {
        Turma turma = new Turma("T-1", "Empresa", "Módulo", LocalDate.of(2026, 11, 2), 4);
        LocalDate dataW48 = LocalDate.of(2026, 11, 29);

        assertThrows(NullPointerException.class,
                () -> new PrevisaoTransicao(null, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 48, dataW48));
        assertThrows(NullPointerException.class,
                () -> new PrevisaoTransicao(turma, null, 2026, 48, dataW48));
        assertThrows(NullPointerException.class,
                () -> new PrevisaoTransicao(turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 48, null));

        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 0, 48, dataW48));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 0, dataW48));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 54, dataW48));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2025, 48, dataW48));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 49, dataW48));
    }

    @Test
    void deveExigirDataExataDaTurmaParaCadaTipoDeEventoEmPrevisaoTransicao() {
        Turma turma = new Turma("T-1", "Empresa", "Módulo", LocalDate.of(2026, 11, 2), 4);
        LocalDate fimEsperado = LocalDate.of(2026, 11, 29);
        LocalDate transicaoEsperada = LocalDate.of(2026, 11, 30);

        PrevisaoTransicao encerramento = new PrevisaoTransicao(
                turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 48, fimEsperado);
        PrevisaoTransicao transicao = new PrevisaoTransicao(
                turma, TipoEventoTurma.TRANSICAO_PREVISTA, 2026, 49, transicaoEsperada);
        assertEquals(fimEsperado, encerramento.dataReferencia());
        assertEquals(transicaoEsperada, transicao.dataReferencia());

        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(
                        turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 48, LocalDate.of(2026, 11, 28)));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(
                        turma, TipoEventoTurma.TRANSICAO_PREVISTA, 2026, 49, LocalDate.of(2026, 12, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(
                        turma, TipoEventoTurma.TRANSICAO_PREVISTA, 2026, 48, fimEsperado));
        assertThrows(IllegalArgumentException.class,
                () -> new PrevisaoTransicao(
                        turma, TipoEventoTurma.ENCERRAMENTO_MODULO, 2026, 49, transicaoEsperada));
    }
}
