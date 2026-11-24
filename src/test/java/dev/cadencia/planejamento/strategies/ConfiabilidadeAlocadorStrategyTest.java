package dev.cadencia.planejamento.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.PeriodoDia;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.PedacoAlocado;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiabilidadeAlocadorStrategyTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);
    private static final LocalDate TERCA = SEGUNDA.plusDays(1);

    @Test
    void deveValidarConstrutorFactoryCopiaDefensivaEEntradasDeAlocacao() {
        ConfiabilidadeAlocadorStrategy padrao = new ConfiabilidadeAlocadorStrategy();
        assertThat(padrao.confiabilidadesConfiguradas()).isEmpty();

        assertThatNullPointerException()
                .isThrownBy(() -> ConfiabilidadeAlocadorStrategy.comConfiabilidade(null));

        FaixaHorario faixaSegManha = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);
        Map<FaixaHorario, ConfiabilidadeFaixa> comValorNulo = new HashMap<>();
        comValorNulo.put(faixaSegManha, null);
        assertThatNullPointerException()
                .isThrownBy(() -> ConfiabilidadeAlocadorStrategy.comConfiabilidade(comValorNulo));

        Map<FaixaHorario, ConfiabilidadeFaixa> mapaMutavel = new HashMap<>();
        ConfiabilidadeFaixa cfRuim = new ConfiabilidadeFaixa(faixaSegManha, 3, 0.20, true, true);
        mapaMutavel.put(faixaSegManha, cfRuim);

        ConfiabilidadeAlocadorStrategy configurada =
                ConfiabilidadeAlocadorStrategy.comConfiabilidade(mapaMutavel);
        mapaMutavel.clear();

        assertThat(configurada.confiabilidadesConfiguradas()).containsEntry(faixaSegManha, cfRuim);
        assertThatThrownBy(configurada.confiabilidadesConfiguradas()::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        Bloco bloco = new Bloco(SEGUNDA, LocalTime.of(8, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null);
        Tarefa tarefa = tarefa("t1", "Java", 25, List.of(TipoBloco.PROFUNDO), 1);

        assertThatNullPointerException().isThrownBy(() -> padrao.alocar(null, List.of(tarefa), Map.of()));
        assertThatNullPointerException().isThrownBy(() -> padrao.alocar(List.of(bloco), null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> padrao.alocar(List.of(bloco), List.of(tarefa), null));
        assertThatNullPointerException().isThrownBy(() -> padrao.alocar(List.of(bloco), List.of(tarefa), comValorNulo));
    }

    @Test
    void devePreservarHeuristicaDaFase3QuandoSemAmostrasSuficientesOuIndiceConfiavel() {
        AlocadorStrategy prioridade = new PrioridadeAlocadorStrategy();
        ConfiabilidadeAlocadorStrategy strategy = new ConfiabilidadeAlocadorStrategy();

        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(7, 0), LocalTime.of(8, 30), TipoBloco.PROFUNDO, null),
                new Bloco(SEGUNDA, LocalTime.of(13, 0), LocalTime.of(14, 30), TipoBloco.PROFUNDO, null),
                new Bloco(TERCA, LocalTime.of(19, 0), LocalTime.of(19, 45), TipoBloco.INTERROMPIVEL, null));
        List<Tarefa> tarefas = List.of(
                tarefa("core", "Java Core", 50, List.of(TipoBloco.PROFUNDO), 1),
                tarefa("junit", "Testes JUnit", 25, List.of(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL), 2),
                tarefa("cards", "Flashcards", 25, List.of(TipoBloco.INTERROMPIVEL), 3));

        ResultadoAlocacao esperadoFase3 = prioridade.alocar(blocos, tarefas);

        // Construtor padrão sem faixas restritas -> idêntico à Fase 3
        assertThat(strategy.alocar(blocos, tarefas)).isEqualTo(esperadoFase3);

        // Faixas com poucas amostras (false, false) ou no limite (true, false) preservam Fase 3
        Map<FaixaHorario, ConfiabilidadeFaixa> mapaNaoDegradado = Map.of(
                new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA),
                new ConfiabilidadeFaixa(new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA), 2, 0.0, false, false),
                new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.TARDE),
                new ConfiabilidadeFaixa(new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.TARDE), 3, 0.50, true, false));

        assertThat(strategy.alocar(blocos, tarefas, mapaNaoDegradado)).isEqualTo(esperadoFase3);
        assertThat(ConfiabilidadeAlocadorStrategy.comConfiabilidade(mapaNaoDegradado).alocar(blocos, tarefas))
                .isEqualTo(esperadoFase3);
    }

    @Test
    void deveAceitarSomenteInterrompivelEProfundoInterrompivelEmBlocoProfundoDegradado() {
        FaixaHorario faixaRuim = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);
        Map<FaixaHorario, ConfiabilidadeFaixa> mapaDegradado = Map.of(
                faixaRuim, new ConfiabilidadeFaixa(faixaRuim, 4, 0.25, true, true));

        ConfiabilidadeAlocadorStrategy strategyComFactory =
                ConfiabilidadeAlocadorStrategy.comConfiabilidade(mapaDegradado);

        Bloco segManhaRuim = new Bloco(
                SEGUNDA, LocalTime.of(7, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null);
        Bloco segTardeBoa = new Bloco(
                SEGUNDA, LocalTime.of(13, 0), LocalTime.of(15, 0), TipoBloco.PROFUNDO, null);
        Bloco segManhaInterrompivel = new Bloco(
                SEGUNDA, LocalTime.of(11, 0), LocalTime.of(11, 30), TipoBloco.INTERROMPIVEL, null);

        // Caso 1: [PROFUNDO] exclusivo -> rejeitada do bloco profundo ruim (vai para a tarde boa)
        Tarefa exclusivamenteProfundo = tarefa("core", "Java Core", 25, List.of(TipoBloco.PROFUNDO), 1);
        // Caso 2: [INTERROMPIVEL] apenas -> ACEITA no bloco profundo ruim mesmo sem conter PROFUNDO!
        Tarefa exclusivamenteInterrompivel = tarefa(
                "cards", "Flashcards Java", 25, List.of(TipoBloco.INTERROMPIVEL), 2);
        // Caso 3: [PROFUNDO, INTERROMPIVEL] -> ACEITA no bloco profundo ruim!
        Tarefa profundoEInterrompivel = tarefa(
                "junit", "Testes JUnit", 25, List.of(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL), 3);
        // Extra: tarefa para bloco INTERROMPIVEL normal na faixa ruim continua alocada normalmente
        Tarefa revisaoExtra = tarefa("rev", "Revisão", 25, List.of(TipoBloco.INTERROMPIVEL), 4);

        ResultadoAlocacao resultado = strategyComFactory.alocar(
                List.of(segManhaRuim, segManhaInterrompivel, segTardeBoa),
                List.of(exclusivamenteProfundo, exclusivamenteInterrompivel, profundoEInterrompivel, revisaoExtra));

        assertThat(resultado.naoAlocados()).isEmpty();
        assertThat(resultado.alocados()).hasSize(4);

        // seg 07:00–07:25 (PROFUNDO degradado) <- Flashcards Java ([INTERROMPIVEL])
        PedacoAlocado a0 = resultado.alocados().get(0);
        assertThat(a0.pedaco().tarefa().id()).isEqualTo("cards");
        assertThat(a0.blocoOrigem()).isEqualTo(segManhaRuim);
        assertThat(a0.inicio()).isEqualTo(LocalTime.of(7, 0));

        // seg 07:25–07:50 (PROFUNDO degradado) <- Testes JUnit ([PROFUNDO, INTERROMPIVEL])
        PedacoAlocado a1 = resultado.alocados().get(1);
        assertThat(a1.pedaco().tarefa().id()).isEqualTo("junit");
        assertThat(a1.blocoOrigem()).isEqualTo(segManhaRuim);
        assertThat(a1.inicio()).isEqualTo(LocalTime.of(7, 25));

        // seg 07:50–08:15 (PROFUNDO degradado) <- Revisão ([INTERROMPIVEL])
        PedacoAlocado a2 = resultado.alocados().get(2);
        assertThat(a2.pedaco().tarefa().id()).isEqualTo("rev");
        assertThat(a2.inicio()).isEqualTo(LocalTime.of(7, 50));

        // seg 13:00–13:25 (PROFUNDO bom) <- Java Core ([PROFUNDO])
        PedacoAlocado a3 = resultado.alocados().get(3);
        assertThat(a3.pedaco().tarefa().id()).isEqualTo("core");
        assertThat(a3.blocoOrigem()).isEqualTo(segTardeBoa);
        assertThat(a3.inicio()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void deveEnviarExclusivamenteProfundoParaNaoAlocadosQuandoTodosOsBlocosProfundosForemRuins() {
        FaixaHorario faixaRuim = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);
        Map<FaixaHorario, ConfiabilidadeFaixa> mapa = Map.of(
                faixaRuim, new ConfiabilidadeFaixa(faixaRuim, 3, 0.0, true, true));
        ConfiabilidadeAlocadorStrategy strategy = new ConfiabilidadeAlocadorStrategy();

        Bloco segManhaRuim = new Bloco(
                SEGUNDA, LocalTime.of(7, 0), LocalTime.of(8, 30), TipoBloco.PROFUNDO, null);
        Tarefa apenasProfundo = tarefa("core", "Java Core", 25, List.of(TipoBloco.PROFUNDO), 1);
        Tarefa apenasInterrompivel = tarefa("cards", "Flashcards", 25, List.of(TipoBloco.INTERROMPIVEL), 2);

        ResultadoAlocacao resultado = strategy.alocar(
                List.of(segManhaRuim), List.of(apenasProfundo, apenasInterrompivel), mapa);

        assertThat(resultado.alocados()).hasSize(1);
        assertThat(resultado.alocados().get(0).pedaco().tarefa().id()).isEqualTo("cards");
        assertThat(resultado.naoAlocados()).hasSize(1);
        assertThat(resultado.naoAlocados().get(0).tarefa().id()).isEqualTo("core");
    }

    @Test
    void deveEquilibrarCargaEntreDiasComConfiabilidadePreservandoRestricaoEIdempotencia() {
        FaixaHorario segManhaRuim = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);
        Map<FaixaHorario, ConfiabilidadeFaixa> mapa = Map.of(
                segManhaRuim, new ConfiabilidadeFaixa(segManhaRuim, 4, 0.25, true, true));
        ConfiabilidadeAlocadorStrategy strategy =
                ConfiabilidadeAlocadorStrategy.comConfiabilidade(mapa);

        List<Bloco> blocos = List.of(
                new Bloco(SEGUNDA, LocalTime.of(7, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null),
                new Bloco(SEGUNDA, LocalTime.of(13, 0), LocalTime.of(15, 0), TipoBloco.PROFUNDO, null),
                new Bloco(TERCA, LocalTime.of(7, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null));

        // 50 min exclusivo PROFUNDO (p1): seg manhã recusado -> 1/2 Seg 13:00 (25m), 2/2 Ter 07:00 (25m)
        Tarefa profundo = tarefa("core", "Java Core", 50, List.of(TipoBloco.PROFUNDO), 1);
        // 25 min INTERROMPIVEL (p2): Seg=25m, Ter=25m -> desempate cronológico -> Seg 07:00 (PROFUNDO degradado aceita!)
        Tarefa somenteInterrompivel =
                tarefa("cards", "Flashcards", 25, List.of(TipoBloco.INTERROMPIVEL), 2);
        // 25 min [PROFUNDO, INTERROMPIVEL] (p3): Ter=25 < Seg=50 -> vai para Ter 07:25
        Tarefa flexivel =
                tarefa("junit", "Testes JUnit", 25, List.of(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL), 3);

        ResultadoAlocacao primeira = strategy.alocar(blocos, List.of(profundo, somenteInterrompivel, flexivel));
        ResultadoAlocacao segunda = strategy.alocar(blocos, List.of(profundo, somenteInterrompivel, flexivel));

        assertThat(primeira).isEqualTo(segunda);
        assertThat(primeira.naoAlocados()).isEmpty();
        assertThat(primeira.alocados()).hasSize(4);

        assertThat(primeira.alocados().get(0).data()).isEqualTo(SEGUNDA);
        assertThat(primeira.alocados().get(0).inicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(primeira.alocados().get(0).pedaco().tarefa().id()).isEqualTo("cards");

        assertThat(primeira.alocados().get(1).data()).isEqualTo(SEGUNDA);
        assertThat(primeira.alocados().get(1).inicio()).isEqualTo(LocalTime.of(13, 0));
        assertThat(primeira.alocados().get(1).pedaco().tarefa().id()).isEqualTo("core");

        assertThat(primeira.alocados().get(2).data()).isEqualTo(TERCA);
        assertThat(primeira.alocados().get(2).inicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(primeira.alocados().get(2).pedaco().tarefa().id()).isEqualTo("core");

        assertThat(primeira.alocados().get(3).data()).isEqualTo(TERCA);
        assertThat(primeira.alocados().get(3).inicio()).isEqualTo(LocalTime.of(7, 25));
        assertThat(primeira.alocados().get(3).pedaco().tarefa().id()).isEqualTo("junit");
    }

    private static Tarefa tarefa(
            String id, String titulo, int minutos, List<TipoBloco> tiposAceitos, int prioridade) {
        return new Tarefa(id, titulo, Duration.ofMinutes(minutos), tiposAceitos, prioridade);
    }
}
