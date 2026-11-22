package dev.cadencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TarefaTest {

    @Test
    void deveCriarTarefaComTamanhoPadraoDe25MinProtegendoImutabilidadeDeTiposAceitos() {
        List<TipoBloco> tiposMutaveis = new ArrayList<>(List.of(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL));

        Tarefa tarefa = new Tarefa(
                "java-collections",
                "Java Collections",
                Duration.ofMinutes(150),
                tiposMutaveis,
                1);
        tiposMutaveis.clear();

        assertThat(tarefa.tamanhoPedaco()).isEqualTo(Duration.ofMinutes(25));
        assertThat(tarefa.tiposAceitos()).containsExactly(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL);
        assertThatThrownBy(() -> tarefa.tiposAceitos().add(TipoBloco.VIGILANCIA))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tarefa.tiposAceitos().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveDividirDivisaoExataSemGerarPedacoVazioExtra() {
        Tarefa tarefa = new Tarefa(
                "t1",
                "Java Collections",
                Duration.ofMinutes(150),
                List.of(TipoBloco.PROFUNDO),
                1);

        List<Pedaco> pedacos = tarefa.dividirEmPedacos();

        assertThat(pedacos).hasSize(6);
        assertThat(pedacos)
                .extracting(Pedaco::numero, Pedaco::total, Pedaco::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 6, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(2, 6, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(3, 6, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(4, 6, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(5, 6, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(6, 6, Duration.ofMinutes(25)));
        assertThat(somarDuracoes(pedacos)).isEqualTo(tarefa.duracaoTotal());
        assertThatThrownBy(pedacos::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveAlocarSomenteORestanteNoUltimoPedaco() {
        Tarefa tarefaPadrao = new Tarefa(
                "t2",
                "Spring Boot",
                Duration.ofMinutes(110),
                List.of(TipoBloco.PROFUNDO),
                2);

        List<Pedaco> pedacos = tarefaPadrao.dividirEmPedacos();

        assertThat(pedacos)
                .extracting(Pedaco::numero, Pedaco::total, Pedaco::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 5, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(2, 5, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(3, 5, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(4, 5, Duration.ofMinutes(25)),
                        org.assertj.core.groups.Tuple.tuple(5, 5, Duration.ofMinutes(10)));
        assertThat(somarDuracoes(pedacos)).isEqualTo(Duration.ofMinutes(110));

        Tarefa custom = new Tarefa(
                "t3",
                "JPA",
                Duration.ofMinutes(95),
                List.of(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL),
                Duration.ofMinutes(30),
                3);
        List<Pedaco> pedacosCustom = custom.dividirEmPedacos();
        assertThat(pedacosCustom)
                .extracting(Pedaco::numero, Pedaco::total, Pedaco::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 4, Duration.ofMinutes(30)),
                        org.assertj.core.groups.Tuple.tuple(2, 4, Duration.ofMinutes(30)),
                        org.assertj.core.groups.Tuple.tuple(3, 4, Duration.ofMinutes(30)),
                        org.assertj.core.groups.Tuple.tuple(4, 4, Duration.ofMinutes(5)));
        assertThat(somarDuracoes(pedacosCustom)).isEqualTo(Duration.ofMinutes(95));
    }

    @Test
    void deveGerarUmUnicoPedacoComDuracaoIntegralQuandoTarefaForMenorOuIgualAoPadrao() {
        Tarefa menorQuePadrao = new Tarefa(
                "t4",
                "Flashcards",
                Duration.ofMinutes(15),
                List.of(TipoBloco.INTERROMPIVEL, TipoBloco.VIGILANCIA),
                1);

        List<Pedaco> pedacosMenor = menorQuePadrao.dividirEmPedacos();
        assertThat(pedacosMenor)
                .extracting(Pedaco::tarefa, Pedaco::numero, Pedaco::total, Pedaco::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(menorQuePadrao, 1, 1, Duration.ofMinutes(15)));
        assertThat(somarDuracoes(pedacosMenor)).isEqualTo(Duration.ofMinutes(15));

        Tarefa igualAoPadrao = new Tarefa(
                "t5",
                "SQL",
                Duration.ofMinutes(25),
                List.of(TipoBloco.INTERROMPIVEL),
                2);
        List<Pedaco> pedacosIgual = igualAoPadrao.dividirEmPedacos();
        assertThat(pedacosIgual)
                .extracting(Pedaco::numero, Pedaco::total, Pedaco::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 1, Duration.ofMinutes(25)));
        assertThat(somarDuracoes(pedacosIgual)).isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void deveValidarInvariantesDeTarefaEPedaco() {
        List<TipoBloco> tipos = List.of(TipoBloco.PROFUNDO);
        Duration umHora = Duration.ofHours(1);
        Duration pedacoPadrao = Duration.ofMinutes(25);

        assertThatNullPointerException()
                .isThrownBy(() -> new Tarefa(null, "Título", umHora, tipos, pedacoPadrao, 1));
        assertThatThrownBy(() -> new Tarefa("  ", "Título", umHora, tipos, pedacoPadrao, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException()
                .isThrownBy(() -> new Tarefa("id", null, umHora, tipos, pedacoPadrao, 1));
        assertThatThrownBy(() -> new Tarefa("id", "  ", umHora, tipos, pedacoPadrao, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatNullPointerException()
                .isThrownBy(() -> new Tarefa("id", "Título", null, tipos, pedacoPadrao, 1));
        assertThatThrownBy(() -> new Tarefa("id", "Título", Duration.ZERO, tipos, pedacoPadrao, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tarefa("id", "Título", Duration.ofMinutes(-10), tipos, pedacoPadrao, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatNullPointerException()
                .isThrownBy(() -> new Tarefa("id", "Título", umHora, null, pedacoPadrao, 1));
        List<TipoBloco> tiposComNulo = Arrays.asList(TipoBloco.PROFUNDO, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new Tarefa("id", "Título", umHora, tiposComNulo, pedacoPadrao, 1));
        assertThatThrownBy(() -> new Tarefa("id", "Título", umHora, List.of(), pedacoPadrao, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatNullPointerException()
                .isThrownBy(() -> new Tarefa("id", "Título", umHora, tipos, null, 1));
        assertThatThrownBy(() -> new Tarefa("id", "Título", umHora, tipos, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tarefa("id", "Título", umHora, tipos, Duration.ofMinutes(-5), 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Tarefa("id", "Título", umHora, tipos, pedacoPadrao, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tarefa("id", "Título", umHora, tipos, pedacoPadrao, -1))
                .isInstanceOf(IllegalArgumentException.class);

        Tarefa valida = new Tarefa("id", "Título", umHora, tipos, 1);
        Pedaco pedacoFinal = new Pedaco(valida, 3, 3, Duration.ofMinutes(10));
        assertThat(pedacoFinal.numero()).isEqualTo(3);

        assertThatNullPointerException()
                .isThrownBy(() -> new Pedaco(null, 1, 3, Duration.ofMinutes(25)));
        assertThatThrownBy(() -> new Pedaco(valida, 1, 0, Duration.ofMinutes(25)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pedaco(valida, 1, -2, Duration.ofMinutes(25)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pedaco(valida, 0, 3, Duration.ofMinutes(25)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pedaco(valida, -1, 3, Duration.ofMinutes(25)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pedaco(valida, 4, 3, Duration.ofMinutes(25)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException()
                .isThrownBy(() -> new Pedaco(valida, 1, 3, null));
        assertThatThrownBy(() -> new Pedaco(valida, 1, 3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pedaco(valida, 1, 3, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Duration somarDuracoes(List<Pedaco> pedacos) {
        return pedacos.stream()
                .map(Pedaco::duracao)
                .reduce(Duration.ZERO, Duration::plus);
    }
}
