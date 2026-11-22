package dev.cadencia.planejamento.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.PedacoAlocado;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrioridadeAlocadorStrategyTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);
    private static final LocalDate TERCA = SEGUNDA.plusDays(1);
    private static final LocalDate QUARTA = SEGUNDA.plusDays(2);

    private final AlocadorStrategy strategy = new PrioridadeAlocadorStrategy();

    @Test
    void devePriorizarMenorNumeroPrimeiroEManterOrdemDoRoadmapNoEmpate() {
        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 50), TipoBloco.PROFUNDO, null),
                new Bloco(SEGUNDA, LocalTime.of(9, 0), LocalTime.of(9, 50), TipoBloco.PROFUNDO, null));

        Tarefa sql = tarefa("sql", "SQL", 25, List.of(TipoBloco.PROFUNDO), 3);
        Tarefa javaCore = tarefa("java-core", "Java Core", 25, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa collections = tarefa("collections", "Collections", 25, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa jpa = tarefa("jpa", "JPA", 25, List.of(TipoBloco.PROFUNDO), 2);

        ResultadoAlocacao resultado = strategy.alocar(blocos, List.of(sql, javaCore, collections, jpa));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(4);

        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "java-core", 1, 1);
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "08:25", "08:50", "collections", 1, 1);
        assertAlocacao(resultado.alocados().get(2), SEGUNDA, "09:00", "09:25", "jpa", 1, 1);
        assertAlocacao(resultado.alocados().get(3), SEGUNDA, "09:25", "09:50", "sql", 1, 1);
    }

    @Test
    void deveAlocarSequencialmenteEmBlocosOrdenadosCronologicamenteComPedacoFinalMenor() {
        List<Bloco> blocosForaDeOrdem = List.of(
                new Bloco(TERCA, LocalTime.of(20, 0), LocalTime.of(21, 0), TipoBloco.PROFUNDO, null),
                new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 50), TipoBloco.PROFUNDO, null));

        Tarefa collections = tarefa("collections", "Java Collections", 65, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa revisao = new Tarefa(
                "revisao",
                "Revisão Rápida",
                Duration.ofMinutes(40),
                List.of(TipoBloco.PROFUNDO),
                Duration.ofMinutes(40),
                2);

        ResultadoAlocacao resultado = strategy.alocar(blocosForaDeOrdem, List.of(collections, revisao));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(4);

        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "collections", 1, 3);
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "08:25", "08:50", "collections", 2, 3);
        assertAlocacao(resultado.alocados().get(2), TERCA, "20:00", "20:15", "collections", 3, 3);
        assertAlocacao(resultado.alocados().get(3), TERCA, "20:15", "20:55", "revisao", 1, 1);
    }

    @Test
    void devePreservarOrdemOriginalDosBlocosSemUsarDescricaoComoDesempate() {
        Bloco primeiroNaEntrada = new Bloco(
                SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 25), TipoBloco.PROFUNDO, "Z - Primeiro");
        Bloco segundoNaEntrada = new Bloco(
                SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 25), TipoBloco.PROFUNDO, "A - Segundo");

        Tarefa javaCore = tarefa("java-core", "Java Core", 25, List.of(TipoBloco.PROFUNDO), 1);

        ResultadoAlocacao resultado = strategy.alocar(
                List.of(primeiroNaEntrada, segundoNaEntrada), List.of(javaCore));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(1);
        assertThat(resultado.alocados().get(0).blocoOrigem()).isEqualTo(primeiroNaEntrada);
        assertThat(resultado.alocados().get(0).data()).isEqualTo(SEGUNDA);
    }

    @Test
    void deveSelecionarBlocoPorTipoCompativelEOrdenarResultadoCronologicamente() {
        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(9, 0), TipoBloco.VIGILANCIA, "Plantão"),
                new Bloco(TERCA, LocalTime.of(20, 0), LocalTime.of(21, 0), TipoBloco.PROFUNDO, null),
                new Bloco(QUARTA, LocalTime.of(14, 0), LocalTime.of(15, 0), TipoBloco.INTERROMPIVEL, null));

        Tarefa springBoot = tarefa("spring-boot", "Spring Boot", 50, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa flashcards = tarefa(
                "flashcards",
                "Flashcards Java",
                50,
                List.of(TipoBloco.INTERROMPIVEL, TipoBloco.VIGILANCIA),
                2);

        ResultadoAlocacao resultado = strategy.alocar(blocos, List.of(springBoot, flashcards));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(4);

        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "flashcards", 1, 2);
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "08:25", "08:50", "flashcards", 2, 2);
        assertAlocacao(resultado.alocados().get(2), TERCA, "20:00", "20:25", "spring-boot", 1, 2);
        assertAlocacao(resultado.alocados().get(3), TERCA, "20:25", "20:50", "spring-boot", 2, 2);
        assertThat(resultado.alocados().get(0).blocoOrigem().tipo()).isEqualTo(TipoBloco.VIGILANCIA);
        assertThat(resultado.alocados().get(2).blocoOrigem().tipo()).isEqualTo(TipoBloco.PROFUNDO);
    }

    @Test
    void deveRegistrarPedacosNaoAlocadosQuandoNaoHouverTempoOuBlocoCompativel() {
        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 30), TipoBloco.INTERROMPIVEL, null));

        Tarefa profundo = tarefa("spring", "Spring Boot", 50, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa interrompivel = tarefa("flash", "Flashcards", 50, List.of(TipoBloco.INTERROMPIVEL), 2);

        ResultadoAlocacao resultado = strategy.alocar(blocos, List.of(interrompivel, profundo));

        assertThat(resultado.alocados()).hasSize(1);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "flash", 1, 2);

        List<Pedaco> naoAlocados = resultado.naoAlocados();
        assertThat(naoAlocados).hasSize(3);
        assertThat(naoAlocados.get(0).tarefa().id()).isEqualTo("spring");
        assertThat(naoAlocados.get(0).numero()).isEqualTo(1);
        assertThat(naoAlocados.get(1).tarefa().id()).isEqualTo("spring");
        assertThat(naoAlocados.get(1).numero()).isEqualTo(2);
        assertThat(naoAlocados.get(2).tarefa().id()).isEqualTo("flash");
        assertThat(naoAlocados.get(2).numero()).isEqualTo(2);
    }

    @Test
    void deveImpedirWrapAroundDeHorarioQuandoBlocoTerminaPertoDaMeiaNoite() {
        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(23, 30), LocalTime.of(23, 59), TipoBloco.PROFUNDO, null));
        Tarefa noturna = tarefa("noturna", "Estudo Noturno", 50, List.of(TipoBloco.PROFUNDO), 1);

        ResultadoAlocacao resultado = strategy.alocar(blocos, List.of(noturna));

        assertThat(resultado.alocados()).hasSize(1);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "23:30", "23:55", "noturna", 1, 2);
        assertThat(resultado.naoAlocados()).hasSize(1);
        assertThat(resultado.naoAlocados().get(0).numero()).isEqualTo(2);
    }

    @Test
    void deveTratarListasVaziasParametrosNulosEImutabilidade() {
        Bloco bloco = new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null);
        Tarefa t1 = tarefa("t1", "Tarefa 1", 50, List.of(TipoBloco.PROFUNDO), 1);

        ResultadoAlocacao semTarefas = strategy.alocar(List.of(bloco), List.of());
        assertThat(semTarefas.alocados()).isEmpty();
        assertThat(semTarefas.naoAlocados()).isEmpty();

        ResultadoAlocacao semBlocos = strategy.alocar(List.of(), List.of(t1));
        assertThat(semBlocos.alocados()).isEmpty();
        assertThat(semBlocos.naoAlocados()).hasSize(2);

        List<Bloco> blocosMutaveis = new ArrayList<>(List.of(bloco));
        List<Tarefa> tarefasMutaveis = new ArrayList<>(List.of(t1));
        ResultadoAlocacao resultado = strategy.alocar(blocosMutaveis, tarefasMutaveis);
        assertThat(blocosMutaveis).containsExactly(bloco);
        assertThat(tarefasMutaveis).containsExactly(t1);
        assertThatThrownBy(resultado.alocados()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(resultado.naoAlocados()::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatNullPointerException().isThrownBy(() -> strategy.alocar(null, List.of(t1)));
        assertThatNullPointerException().isThrownBy(() -> strategy.alocar(List.of(bloco), null));

        List<Bloco> blocosComNulo = new ArrayList<>();
        blocosComNulo.add(null);
        assertThatNullPointerException().isThrownBy(() -> strategy.alocar(blocosComNulo, List.of(t1)));

        List<Tarefa> tarefasComNulo = new ArrayList<>();
        tarefasComNulo.add(null);
        assertThatNullPointerException().isThrownBy(() -> strategy.alocar(List.of(bloco), tarefasComNulo));
    }

    private static Tarefa tarefa(
            String id, String titulo, int minutos, List<TipoBloco> tiposAceitos, int prioridade) {
        return new Tarefa(id, titulo, Duration.ofMinutes(minutos), tiposAceitos, prioridade);
    }

    private static void assertAlocacao(
            PedacoAlocado alocado,
            LocalDate dataEsperada,
            String inicioEsperado,
            String fimEsperado,
            String idEsperado,
            int numeroEsperado,
            int totalEsperado) {
        assertThat(alocado.blocoOrigem().data()).isEqualTo(dataEsperada);
        assertThat(alocado.data()).isEqualTo(dataEsperada);
        assertThat(alocado.inicio()).isEqualTo(LocalTime.parse(inicioEsperado));
        assertThat(alocado.fim()).isEqualTo(LocalTime.parse(fimEsperado));
        assertThat(alocado.pedaco().tarefa().id()).isEqualTo(idEsperado);
        assertThat(alocado.pedaco().numero()).isEqualTo(numeroEsperado);
        assertThat(alocado.pedaco().total()).isEqualTo(totalEsperado);
    }
}
