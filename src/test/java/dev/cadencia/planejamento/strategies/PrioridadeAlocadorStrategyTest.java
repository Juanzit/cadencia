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
                new Bloco(TERCA, LocalTime.of(20, 0), LocalTime.of(21, 5), TipoBloco.PROFUNDO, null),
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
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "08:25", "08:40", "collections", 3, 3);
        assertAlocacao(resultado.alocados().get(2), TERCA, "20:00", "20:25", "collections", 2, 3);
        assertAlocacao(resultado.alocados().get(3), TERCA, "20:25", "21:05", "revisao", 1, 1);
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
        assertAlocacao(resultado.alocados().get(1), TERCA, "20:00", "20:25", "spring-boot", 1, 2);
        assertAlocacao(resultado.alocados().get(2), TERCA, "20:25", "20:50", "spring-boot", 2, 2);
        assertAlocacao(resultado.alocados().get(3), QUARTA, "14:00", "14:25", "flashcards", 2, 2);
        assertThat(resultado.alocados().get(0).blocoOrigem().tipo()).isEqualTo(TipoBloco.VIGILANCIA);
        assertThat(resultado.alocados().get(1).blocoOrigem().tipo()).isEqualTo(TipoBloco.PROFUNDO);
        assertThat(resultado.alocados().get(3).blocoOrigem().tipo()).isEqualTo(TipoBloco.INTERROMPIVEL);
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
        assertThat(strategy.confiabilidadesConfiguradas()).isEmpty();
        assertThat(strategy.alocar(List.of(bloco), List.of(t1), java.util.Map.of())).isEqualTo(resultado);
        assertThatNullPointerException().isThrownBy(() -> strategy.alocar(List.of(bloco), List.of(t1), null));
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

    @Test
    void deveDistribuirEntreDiasDeMenorCargaEDesempatarCronologicamenteComIdempotencia() {
        LocalDate quinta = QUARTA.plusDays(1);
        List<Bloco> blocos = List.of(
                new Bloco(quinta, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null),
                new Bloco(SEGUNDA, LocalTime.of(7, 0), LocalTime.of(12, 0), TipoBloco.PROFUNDO, null),
                new Bloco(QUARTA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null),
                new Bloco(TERCA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null));

        // 4 pedaços de 25 min -> 1 por dia com carga equilibrada (25 min em Seg, Ter, Qua, Qui)
        Tarefa java = tarefa("java", "Java Core", 100, List.of(TipoBloco.PROFUNDO), 1);

        ResultadoAlocacao primeira = strategy.alocar(blocos, List.of(java));
        ResultadoAlocacao segunda = strategy.alocar(blocos, List.of(java));

        assertThat(primeira).isEqualTo(segunda);
        assertThat(primeira.naoAlocados()).isEmpty();
        assertThat(primeira.alocados()).hasSize(4);
        assertAlocacao(primeira.alocados().get(0), SEGUNDA, "07:00", "07:25", "java", 1, 4);
        assertAlocacao(primeira.alocados().get(1), TERCA, "08:00", "08:25", "java", 2, 4);
        assertAlocacao(primeira.alocados().get(2), QUARTA, "08:00", "08:25", "java", 3, 4);
        assertAlocacao(primeira.alocados().get(3), quinta, "08:00", "08:25", "java", 4, 4);
    }

    @Test
    void deveBalancearPelaSomaDaDuracaoAlocadaQuandoPedacosTiveremDuracoesDiferentes() {
        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null),
                new Bloco(TERCA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null));

        // Prio 1: 45 min (1 pedaço -> Seg=45)
        Tarefa longa = new Tarefa(
                "longa",
                "Projeto",
                Duration.ofMinutes(45),
                List.of(TipoBloco.PROFUNDO),
                Duration.ofMinutes(45),
                1);
        // Prio 2: 40 min divididos em 20 + 20 (Ter=0 -> 1/2 vai para Ter=20; Ter=20 < Seg=45 -> 2/2 vai para Ter=40)
        Tarefa media = new Tarefa(
                "media",
                "Leitura",
                Duration.ofMinutes(40),
                List.of(TipoBloco.PROFUNDO),
                Duration.ofMinutes(20),
                2);
        // Prio 3: 15 min -> Ter=40 < Seg=45 -> também vai para Ter (carga final Ter=55, Seg=45; antes não conta quantidade de pedaços!)
        Tarefa curta = new Tarefa(
                "curta",
                "Revisão",
                Duration.ofMinutes(15),
                List.of(TipoBloco.PROFUNDO),
                Duration.ofMinutes(15),
                3);

        ResultadoAlocacao resultado = strategy.alocar(blocos, List.of(curta, longa, media));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(4);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:45", "longa", 1, 1);
        assertAlocacao(resultado.alocados().get(1), TERCA, "08:00", "08:20", "media", 1, 2);
        assertAlocacao(resultado.alocados().get(2), TERCA, "08:20", "08:40", "media", 2, 2);
        assertAlocacao(resultado.alocados().get(3), TERCA, "08:40", "08:55", "curta", 1, 1);
    }

    @Test
    void deveConcentrarEmUmUnicoDiaEPreservarPrioridadeQuandoOutrosDiasNaoForemCompativeis() {
        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null),
                new Bloco(TERCA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.INTERROMPIVEL, null),
                new Bloco(QUARTA, LocalTime.of(8, 0), LocalTime.of(10, 0), TipoBloco.VIGILANCIA, null));

        Tarefa urgente = tarefa("urgente", "Arquitetura", 50, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa secundaria = tarefa("secundaria", "Testes", 25, List.of(TipoBloco.PROFUNDO), 2);

        ResultadoAlocacao resultado = strategy.alocar(blocos, List.of(secundaria, urgente));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(3);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "urgente", 1, 2);
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "08:25", "08:50", "urgente", 2, 2);
        assertAlocacao(resultado.alocados().get(2), SEGUNDA, "08:50", "09:15", "secundaria", 1, 1);
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
