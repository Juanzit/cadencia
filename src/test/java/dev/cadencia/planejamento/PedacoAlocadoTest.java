package dev.cadencia.planejamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PedacoAlocadoTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);

    @Test
    void deveCriarPedacoAlocadoEValidarConsistenciaDeDataLimitesEDuracao() {
        Bloco blocoOrigem = new Bloco(
                SEGUNDA, LocalTime.of(20, 0), LocalTime.of(22, 0), TipoBloco.PROFUNDO, null);
        Tarefa tarefa = new Tarefa(
                "java-collections",
                "Java Collections",
                Duration.ofMinutes(50),
                List.of(TipoBloco.PROFUNDO),
                1);
        Pedaco pedaco = tarefa.dividirEmPedacos().get(0);

        PedacoAlocado alocado = new PedacoAlocado(
                blocoOrigem, SEGUNDA, LocalTime.of(20, 0), LocalTime.of(20, 25), pedaco);
        assertThat(alocado.blocoOrigem()).isEqualTo(blocoOrigem);
        assertThat(alocado.data()).isEqualTo(SEGUNDA);
        assertThat(alocado.inicio()).isEqualTo(LocalTime.of(20, 0));
        assertThat(alocado.fim()).isEqualTo(LocalTime.of(20, 25));
        assertThat(alocado.pedaco()).isEqualTo(pedaco);

        assertThatNullPointerException()
                .isThrownBy(() -> new PedacoAlocado(
                        null, SEGUNDA, LocalTime.of(20, 0), LocalTime.of(20, 25), pedaco));
        assertThatNullPointerException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, null, LocalTime.of(20, 0), LocalTime.of(20, 25), pedaco));
        assertThatNullPointerException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, null, LocalTime.of(20, 25), pedaco));
        assertThatNullPointerException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, LocalTime.of(20, 0), null, pedaco));
        assertThatNullPointerException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, LocalTime.of(20, 0), LocalTime.of(20, 25), null));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA.plusDays(1), LocalTime.of(20, 0), LocalTime.of(20, 25), pedaco));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, LocalTime.of(20, 25), LocalTime.of(20, 25), pedaco));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, LocalTime.of(19, 55), LocalTime.of(20, 20), pedaco));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, LocalTime.of(21, 45), LocalTime.of(22, 10), pedaco));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PedacoAlocado(
                        blocoOrigem, SEGUNDA, LocalTime.of(20, 0), LocalTime.of(20, 30), pedaco));
    }

    @Test
    void deveCriarResultadoAlocacaoImutavelEValidarNulos() {
        Bloco blocoOrigem = new Bloco(
                SEGUNDA, LocalTime.of(20, 0), LocalTime.of(21, 0), TipoBloco.PROFUNDO, null);
        Tarefa tarefa = new Tarefa(
                "java-core",
                "Java Core",
                Duration.ofMinutes(50),
                List.of(TipoBloco.PROFUNDO),
                1);
        List<Pedaco> pedacos = tarefa.dividirEmPedacos();
        PedacoAlocado alocado = new PedacoAlocado(
                blocoOrigem, SEGUNDA, LocalTime.of(20, 0), LocalTime.of(20, 25), pedacos.get(0));

        List<PedacoAlocado> alocadosMutaveis = new ArrayList<>(List.of(alocado));
        List<Pedaco> naoAlocadosMutaveis = new ArrayList<>(List.of(pedacos.get(1)));

        ResultadoAlocacao resultado = new ResultadoAlocacao(alocadosMutaveis, naoAlocadosMutaveis);
        alocadosMutaveis.clear();
        naoAlocadosMutaveis.clear();

        assertThat(resultado.alocados()).containsExactly(alocado);
        assertThat(resultado.naoAlocados()).containsExactly(pedacos.get(1));
        assertThatThrownBy(resultado.alocados()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(resultado.naoAlocados()::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatNullPointerException().isThrownBy(() -> new ResultadoAlocacao(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new ResultadoAlocacao(List.of(), null));

        List<PedacoAlocado> comElementoNulo = new ArrayList<>();
        comElementoNulo.add(null);
        assertThatNullPointerException().isThrownBy(() -> new ResultadoAlocacao(comElementoNulo, List.of()));
    }
}
