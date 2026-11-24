package dev.cadencia.planejamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.cadencia.agenda.IntervaloOcupado;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.agenda.TipoIntervaloOcupado;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.PeriodoDia;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.strategies.AlocadorStrategy;
import dev.cadencia.planejamento.strategies.ConfiabilidadeAlocadorStrategy;
import dev.cadencia.planejamento.strategies.PrioridadeAlocadorStrategy;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlanejamentoServiceTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);
    private static final LocalDate TERCA = SEGUNDA.plusDays(1);

    private final AlocadorStrategy prioridadeStrategy = new PrioridadeAlocadorStrategy();

    @Test
    void deveValidarParametrosDoConstrutorEDoMetodoAlocar() {
        assertThatNullPointerException().isThrownBy(() -> new PlanejamentoService(null));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanejamentoService(null, Duration.ofMinutes(10)));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanejamentoService(prioridadeStrategy, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PlanejamentoService(prioridadeStrategy, Duration.ofMinutes(-1)));

        PlanejamentoService service = new PlanejamentoService(prioridadeStrategy);
        SemanaPlanejada semanaVazia = semanaPlanejada(List.of(), List.of());

        assertThatNullPointerException().isThrownBy(() -> service.alocar(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> service.alocar(semanaVazia, null));

        List<Tarefa> listaComNulo = new ArrayList<>();
        listaComNulo.add(null);
        assertThatNullPointerException().isThrownBy(() -> service.alocar(semanaVazia, listaComNulo));

        ResultadoAlocacao semTarefas = service.alocar(semanaVazia, List.of());
        assertThat(semTarefas.alocados()).isEmpty();
        assertThat(semTarefas.naoAlocados()).isEmpty();
    }

    @Test
    void devePreservarBlocosQuandoFolgaMinimaForZero() {
        PlanejamentoService semFolga = new PlanejamentoService(prioridadeStrategy, Duration.ZERO);
        Bloco blocoAntes = bloco(SEGUNDA, "08:00", "08:25", TipoBloco.PROFUNDO);
        Bloco blocoDepois = bloco(SEGUNDA, "09:25", "09:50", TipoBloco.PROFUNDO);
        IntervaloOcupado aula = ocupado(SEGUNDA, "08:25", 60, "Aula");

        ResultadoAlocacao resultado = semFolga.alocar(
                semanaPlanejada(List.of(blocoAntes, blocoDepois), List.of(aula)),
                List.of(
                        tarefa("t1", "Java", 25, List.of(TipoBloco.PROFUNDO), 1),
                        tarefa("t2", "SQL", 25, List.of(TipoBloco.PROFUNDO), 2)));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(2);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "t1");
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "09:25", "09:50", "t2");
    }

    @Test
    void deveGarantirCompatibilidadeIndividualSemFiltroGlobalESemReiniciarCapacidade() {
        Bloco vigilancia = bloco(SEGUNDA, "08:00", "09:00", TipoBloco.VIGILANCIA);
        Bloco profundo = bloco(SEGUNDA, "09:00", "10:40", TipoBloco.PROFUNDO);

        Tarefa novoConteudo = tarefa("spring", "Spring Boot", 50, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa revisaoLeve = tarefa("flash", "Flashcards", 25, List.of(TipoBloco.VIGILANCIA), 2);
        Tarefa segundoProfundo = tarefa("jpa", "JPA", 50, List.of(TipoBloco.PROFUNDO), 3);
        Tarefa naoCabeProfundo = tarefa("sql", "SQL", 25, List.of(TipoBloco.PROFUNDO), 4);

        PlanejamentoService service = new PlanejamentoService(prioridadeStrategy);
        ResultadoAlocacao resultado = service.alocar(
                semanaPlanejada(List.of(vigilancia, profundo), List.of()),
                List.of(novoConteudo, revisaoLeve, segundoProfundo, naoCabeProfundo));

        assertThat(resultado.alocados()).hasSize(5);
        assertThat(resultado.alocados())
                .allSatisfy(alocado -> assertThat(alocado.pedaco().tarefa().tiposAceitos())
                        .contains(alocado.blocoOrigem().tipo()));

        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "08:00", "08:25", "flash");
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "09:00", "09:25", "spring");
        assertAlocacao(resultado.alocados().get(2), SEGUNDA, "09:25", "09:50", "spring");
        assertAlocacao(resultado.alocados().get(3), SEGUNDA, "09:50", "10:15", "jpa");
        assertAlocacao(resultado.alocados().get(4), SEGUNDA, "10:15", "10:40", "jpa");

        for (int i = 1; i < resultado.alocados().size(); i++) {
            PedacoAlocado anterior = resultado.alocados().get(i - 1);
            PedacoAlocado atual = resultado.alocados().get(i);
            assertThat(atual.inicio()).isAfterOrEqualTo(anterior.fim());
        }

        assertThat(resultado.naoAlocados()).hasSize(1);
        assertThat(resultado.naoAlocados().get(0).tarefa().id()).isEqualTo("sql");
    }

    @Test
    void deveFiltrarAlocacaoIncompativelCasoEstrategiaRetorneViolacaoIndividual() {
        Bloco vigilancia = bloco(SEGUNDA, "08:00", "09:00", TipoBloco.VIGILANCIA);
        Tarefa profundo = tarefa("spring", "Spring Boot", 50, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa aceitaVigilancia = tarefa("cards", "Cards", 25, List.of(TipoBloco.VIGILANCIA), 2);

        AlocadorStrategy estrategiaIngenua = (blocos, tarefas) -> new ResultadoAlocacao(
                List.of(
                        new PedacoAlocado(
                                vigilancia,
                                SEGUNDA,
                                LocalTime.of(8, 0),
                                LocalTime.of(8, 25),
                                tarefas.get(0).dividirEmPedacos().get(0)),
                        new PedacoAlocado(
                                vigilancia,
                                SEGUNDA,
                                LocalTime.of(8, 25),
                                LocalTime.of(8, 50),
                                tarefas.get(1).dividirEmPedacos().get(0))),
                List.of(tarefas.get(0).dividirEmPedacos().get(1)));

        PlanejamentoService service = new PlanejamentoService(estrategiaIngenua);
        ResultadoAlocacao resultado = service.alocar(
                semanaPlanejada(List.of(vigilancia), List.of()),
                List.of(profundo, aceitaVigilancia));

        assertThat(resultado.alocados()).hasSize(1);
        assertThat(resultado.alocados().get(0).pedaco().tarefa().id()).isEqualTo("cards");
        assertThat(resultado.naoAlocados()).hasSize(2);
        assertThat(resultado.naoAlocados().get(0).tarefa().id()).isEqualTo("spring");
        assertThat(resultado.naoAlocados().get(0).numero()).isEqualTo(1);
        assertThat(resultado.naoAlocados().get(1).tarefa().id()).isEqualTo("spring");
        assertThat(resultado.naoAlocados().get(1).numero()).isEqualTo(2);
    }

    @Test
    void deveDescontarFolgaQuandoOcupacaoTerminaNoInicioOuComecaNoFimOuEmAmbasAsExtremidades() {
        Bloco apenasFimAdjacente = new Bloco(
                SEGUNDA, LocalTime.of(7, 0), LocalTime.of(8, 0), TipoBloco.PROFUNDO, "Manhã");
        Bloco ambasExtremidades = new Bloco(
                SEGUNDA, LocalTime.of(9, 0), LocalTime.of(10, 40), TipoBloco.PROFUNDO, "Meio");
        Bloco apenasInicioAdjacente = new Bloco(
                SEGUNDA, LocalTime.of(11, 40), LocalTime.of(12, 15), TipoBloco.INTERROMPIVEL, "Pós-aula");

        List<IntervaloOcupado> ocupacoes = List.of(
                ocupado(SEGUNDA, "08:00", 60, "Treino"),
                ocupado(SEGUNDA, "10:40", 60, "Aula"));

        AtomicReference<List<Bloco>> blocosRecebidos = new AtomicReference<>();
        AtomicInteger chamadas = new AtomicInteger();
        AlocadorStrategy espiandoBlocos = (blocos, tarefas) -> {
            chamadas.incrementAndGet();
            blocosRecebidos.set(blocos);
            return prioridadeStrategy.alocar(blocos, tarefas);
        };

        PlanejamentoService service = new PlanejamentoService(espiandoBlocos);
        ResultadoAlocacao resultado = service.alocar(
                semanaPlanejada(
                        List.of(apenasFimAdjacente, ambasExtremidades, apenasInicioAdjacente), ocupacoes),
                List.of(
                        tarefa("t1", "Java Core", 125, List.of(TipoBloco.PROFUNDO), 1),
                        tarefa("t2", "Flashcards", 25, List.of(TipoBloco.INTERROMPIVEL), 2)));

        assertThat(chamadas.get()).isEqualTo(1);
        assertThat(blocosRecebidos.get()).containsExactly(
                new Bloco(SEGUNDA, LocalTime.of(7, 0), LocalTime.of(7, 50), TipoBloco.PROFUNDO, "Manhã"),
                new Bloco(SEGUNDA, LocalTime.of(9, 10), LocalTime.of(10, 30), TipoBloco.PROFUNDO, "Meio"),
                new Bloco(SEGUNDA, LocalTime.of(11, 50), LocalTime.of(12, 15), TipoBloco.INTERROMPIVEL, "Pós-aula"));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(6);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "07:00", "07:25", "t1");
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "07:25", "07:50", "t1");
        assertAlocacao(resultado.alocados().get(2), SEGUNDA, "09:10", "09:35", "t1");
        assertAlocacao(resultado.alocados().get(3), SEGUNDA, "09:35", "10:00", "t1");
        assertAlocacao(resultado.alocados().get(4), SEGUNDA, "10:00", "10:25", "t1");
        assertAlocacao(resultado.alocados().get(5), SEGUNDA, "11:50", "12:15", "t2");
    }

    @Test
    void naoDeveDescontarFolgaParaOcupacaoProximaNaoAdjacenteOuSemOcupacaoAdjacente() {
        Bloco blocoAntesComGapDe5Min = bloco(SEGUNDA, "07:00", "07:55", TipoBloco.PROFUNDO);
        Bloco blocoDepoisComGapDe5Min = bloco(SEGUNDA, "09:05", "10:00", TipoBloco.PROFUNDO);
        Bloco outroDiaSemOcupacao = bloco(TERCA, "08:00", "08:50", TipoBloco.PROFUNDO);

        IntervaloOcupado aulaNaoAdjacente = ocupado(SEGUNDA, "08:00", 60, "Aula");

        AtomicReference<List<Bloco>> blocosRecebidos = new AtomicReference<>();
        AlocadorStrategy espiandoBlocos = (blocos, tarefas) -> {
            blocosRecebidos.set(blocos);
            return prioridadeStrategy.alocar(blocos, tarefas);
        };

        PlanejamentoService service = new PlanejamentoService(espiandoBlocos);
        ResultadoAlocacao resultado = service.alocar(
                semanaPlanejada(
                        List.of(blocoAntesComGapDe5Min, blocoDepoisComGapDe5Min, outroDiaSemOcupacao),
                        List.of(aulaNaoAdjacente)),
                List.of(tarefa("t1", "Collections", 150, List.of(TipoBloco.PROFUNDO), 1)));

        assertThat(blocosRecebidos.get()).containsExactly(
                blocoAntesComGapDe5Min, blocoDepoisComGapDe5Min, outroDiaSemOcupacao);
        assertThat(resultado.alocados()).hasSize(6);
        assertThat(resultado.naoAlocados()).isEmpty();
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "07:00", "07:25", "t1");
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "07:25", "07:50", "t1");
        assertAlocacao(resultado.alocados().get(2), SEGUNDA, "09:05", "09:30", "t1");
        assertAlocacao(resultado.alocados().get(3), SEGUNDA, "09:30", "09:55", "t1");
        assertAlocacao(resultado.alocados().get(4), TERCA, "08:00", "08:25", "t1");
        assertAlocacao(resultado.alocados().get(5), TERCA, "08:25", "08:50", "t1");
    }

    @Test
    void deveDescartarSemExcecaoBlocosConsumidosIntegralmentePelaFolga() {
        Bloco consumidoNoInicio = bloco(SEGUNDA, "09:00", "09:10", TipoBloco.PROFUNDO);
        Bloco consumidoNoFim = bloco(SEGUNDA, "11:50", "12:00", TipoBloco.PROFUNDO);
        Bloco consumidoDuasFolgas = bloco(SEGUNDA, "13:00", "13:20", TipoBloco.PROFUNDO);
        Bloco consumidoFolgaMenorQueMargem = bloco(SEGUNDA, "14:00", "14:15", TipoBloco.PROFUNDO);

        List<IntervaloOcupado> ocupacoes = List.of(
                ocupado(SEGUNDA, "08:00", 60, "Aula 1"),
                ocupado(SEGUNDA, "12:00", 60, "Almoço"),
                ocupado(SEGUNDA, "13:20", 40, "Reunião"),
                ocupado(SEGUNDA, "14:15", 45, "Treino"));

        PlanejamentoService service = new PlanejamentoService(prioridadeStrategy);
        ResultadoAlocacao resultado = service.alocar(
                semanaPlanejada(
                        List.of(consumidoNoInicio, consumidoNoFim, consumidoDuasFolgas, consumidoFolgaMenorQueMargem),
                        ocupacoes),
                List.of(tarefa("t1", "JPA", 25, List.of(TipoBloco.PROFUNDO), 1)));

        assertThat(resultado.alocados()).isEmpty();
        assertThat(resultado.naoAlocados()).hasSize(1);
    }

    @Test
    void deveTratarMidnightComoLimite24HorasSemWrapAroundOuFalsaAdjacencia() {
        Bloco madrugada = bloco(SEGUNDA, "00:00", "00:25", TipoBloco.PROFUNDO);
        Bloco noturnoAdjacenteAMidnight = bloco(SEGUNDA, "20:00", "23:00", TipoBloco.PROFUNDO);
        Bloco consumidoPertoDaMeiaNoite = bloco(TERCA, "23:55", "23:59", TipoBloco.PROFUNDO);

        IntervaloOcupado plantaoAteMidnight = new IntervaloOcupado(
                SEGUNDA,
                LocalTime.of(23, 0),
                Duration.ofMinutes(60),
                TipoIntervaloOcupado.COMPROMISSO,
                "Plantão");
        IntervaloOcupado compromissoAntesDaMeiaNoite = ocupado(TERCA, "23:00", 55, "Compromisso");

        AtomicReference<List<Bloco>> blocosRecebidos = new AtomicReference<>();
        AlocadorStrategy espiandoBlocos = (blocos, tarefas) -> {
            blocosRecebidos.set(blocos);
            return prioridadeStrategy.alocar(blocos, tarefas);
        };

        PlanejamentoService service = new PlanejamentoService(espiandoBlocos);
        ResultadoAlocacao resultado = service.alocar(
                semanaPlanejada(
                        List.of(madrugada, noturnoAdjacenteAMidnight, consumidoPertoDaMeiaNoite),
                        List.of(plantaoAteMidnight, compromissoAntesDaMeiaNoite)),
                List.of(tarefa("t1", "Estudo", 50, List.of(TipoBloco.PROFUNDO), 1)));

        assertThat(blocosRecebidos.get()).containsExactly(
                bloco(SEGUNDA, "00:00", "00:25", TipoBloco.PROFUNDO),
                bloco(SEGUNDA, "20:00", "22:50", TipoBloco.PROFUNDO));
        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(2);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "00:00", "00:25", "t1");
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "20:00", "20:25", "t1");
    }

    @Test
    void devePropagarConfiabilidadesNaSobrecargaRespeitandoFolgaMinimaEValidandoCompatibilidadeDegradada() {
        Bloco segManhaRuim = bloco(SEGUNDA, "07:00", "08:30", TipoBloco.PROFUNDO);
        Bloco segTardeBoa = bloco(SEGUNDA, "13:00", "14:00", TipoBloco.PROFUNDO);
        IntervaloOcupado aula = ocupado(SEGUNDA, "08:30", 60, "Aula");

        Tarefa exclusivoProfundo = tarefa("core", "Java Core", 25, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa somenteInterrompivel = tarefa("cards", "Flashcards", 25, List.of(TipoBloco.INTERROMPIVEL), 2);
        Tarefa flexivel = tarefa(
                "junit", "Testes JUnit", 25, List.of(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL), 3);

        FaixaHorario faixaRuim = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);
        Map<FaixaHorario, ConfiabilidadeFaixa> mapa = Map.of(
                faixaRuim, new ConfiabilidadeFaixa(faixaRuim, 3, 0.25, true, true));

        PlanejamentoService service = new PlanejamentoService(new ConfiabilidadeAlocadorStrategy());
        SemanaPlanejada semana = semanaPlanejada(List.of(segManhaRuim, segTardeBoa), List.of(aula));

        ResultadoAlocacao resultado = service.alocar(
                semana, List.of(exclusivoProfundo, somenteInterrompivel, flexivel), mapa);

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(3);
        assertAlocacao(resultado.alocados().get(0), SEGUNDA, "07:00", "07:25", "cards");
        assertAlocacao(resultado.alocados().get(1), SEGUNDA, "07:25", "07:50", "junit");
        assertAlocacao(resultado.alocados().get(2), SEGUNDA, "13:00", "13:25", "core");
        assertThat(resultado.alocados().get(0).blocoOrigem().fim()).isEqualTo(LocalTime.of(8, 20));

        // Também valida via ConfiabilidadeAlocadorStrategy.comConfiabilidade sem passar mapa no alocar(2 args)
        PlanejamentoService serviceComEstrategia = new PlanejamentoService(
                ConfiabilidadeAlocadorStrategy.comConfiabilidade(mapa));
        assertThat(serviceComEstrategia.alocar(
                        semana, List.of(exclusivoProfundo, somenteInterrompivel, flexivel)))
                .isEqualTo(resultado);

        // Se uma estratégia violar a proteção alocando tarefa exclusivamente PROFUNDO em faixa ruim, o serviço rejeita
        AlocadorStrategy estrategiaVioladora = (blocos, tarefas) -> new ResultadoAlocacao(
                List.of(new PedacoAlocado(
                        blocos.get(0),
                        SEGUNDA,
                        LocalTime.of(7, 0),
                        LocalTime.of(7, 25),
                        exclusivoProfundo.dividirEmPedacos().get(0))),
                List.of());
        ResultadoAlocacao corrigido = new PlanejamentoService(estrategiaVioladora)
                .alocar(semana, List.of(exclusivoProfundo), mapa);
        assertThat(corrigido.alocados()).isEmpty();
        assertThat(corrigido.naoAlocados()).hasSize(1);

        assertThatNullPointerException().isThrownBy(() -> service.alocar(semana, List.of(), null));
    }

    private static SemanaPlanejada semanaPlanejada(
            List<Bloco> blocos, List<IntervaloOcupado> ocupados) {
        Semana semana = new Semana(2026, 48, List.of(), blocos);
        return new SemanaPlanejada(SEGUNDA, semana, ocupados, List.of());
    }

    private static Bloco bloco(LocalDate data, String inicio, String fim, TipoBloco tipo) {
        return new Bloco(data, LocalTime.parse(inicio), LocalTime.parse(fim), tipo, null);
    }

    private static IntervaloOcupado ocupado(
            LocalDate data, String inicio, int duracaoMin, String descricao) {
        return new IntervaloOcupado(
                data,
                LocalTime.parse(inicio),
                Duration.ofMinutes(duracaoMin),
                TipoIntervaloOcupado.COMPROMISSO,
                descricao);
    }

    private static Tarefa tarefa(
            String id, String titulo, int duracaoMin, List<TipoBloco> tiposAceitos, int prioridade) {
        return new Tarefa(id, titulo, Duration.ofMinutes(duracaoMin), tiposAceitos, prioridade);
    }

    private static void assertAlocacao(
            PedacoAlocado alocado,
            LocalDate dataEsperada,
            String inicioEsperado,
            String fimEsperado,
            String idTarefa) {
        assertThat(alocado.data()).isEqualTo(dataEsperada);
        assertThat(alocado.inicio()).isEqualTo(LocalTime.parse(inicioEsperado));
        assertThat(alocado.fim()).isEqualTo(LocalTime.parse(fimEsperado));
        assertThat(alocado.pedaco().tarefa().id()).isEqualTo(idTarefa);
    }
}
