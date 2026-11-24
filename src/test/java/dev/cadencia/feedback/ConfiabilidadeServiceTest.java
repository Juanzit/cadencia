package dev.cadencia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.PeriodoDia;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.StatusBloco;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfiabilidadeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deveValidarParametrosDeConstrutorEMetodosDeCalculo() {
        FeedbackRepository repo = new MarkdownFeedbackRepository();

        assertThatNullPointerException().isThrownBy(() -> new ConfiabilidadeService(null, tempDir));
        assertThatNullPointerException().isThrownBy(() -> new ConfiabilidadeService(repo, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConfiabilidadeService(repo, Path.of("  ")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfiabilidadeService(repo, tempDir, 0, 3, 0.50));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfiabilidadeService(repo, tempDir, 4, 0, 0.50));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfiabilidadeService(repo, tempDir, 4, 3, -0.01));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfiabilidadeService(repo, tempDir, 4, 3, 1.01));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfiabilidadeService(repo, tempDir, 4, 3, Double.NaN));

        ConfiabilidadeService service = new ConfiabilidadeService(repo, tempDir);
        assertThat(service.janelaSemanas()).isEqualTo(4);
        assertThat(service.minimoAmostras()).isEqualTo(3);
        assertThat(service.limiteConfiabilidade()).isEqualTo(0.50);
        assertThat(service.ehBaixaConfiabilidade(null)).isFalse();

        Semana semana = new Semana(2026, 48, List.of(), List.of());
        assertThatNullPointerException().isThrownBy(() -> service.calcularPorFaixa(null));
        assertThatNullPointerException().isThrownBy(() -> service.calcularPorFaixa(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> service.calcularPorFaixa(semana, null));
        assertThat(service.calcularPorFaixa(semana, List.of())).isEmpty();
        assertThat(service.calcularConfiabilidades(semana, List.of())).isEmpty();
    }

    @Test
    void deveCalcularIndicesComPesosDeterministicosJanelaDeSemanasEIgnorarSemanasFuturas() {
        FeedbackRepository repo = new MarkdownFeedbackRepository();
        ConfiabilidadeService service = new ConfiabilidadeService(repo, tempDir, 4, 3, 0.50);

        FaixaHorario segManha = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);
        FaixaHorario quaTarde = new FaixaHorario(DayOfWeek.WEDNESDAY, PeriodoDia.TARDE);
        FaixaHorario sexNoite = new FaixaHorario(DayOfWeek.FRIDAY, PeriodoDia.NOITE);

        // W43 fica fora da janela de 4 semanas para alvo W48 (janela: W44, W45, W46, W47)
        FeedbackSemanal w43ForaJanela = semana(2026, 43, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.CONCLUIDO),
                item(DayOfWeek.WEDNESDAY, 14, StatusBloco.NAO_ACONTECEU),
                item(DayOfWeek.FRIDAY, 20, StatusBloco.CONCLUIDO)));
        FeedbackSemanal w44 = semana(2026, 44, List.of(
                item(DayOfWeek.MONDAY, 9, StatusBloco.NAO_ACONTECEU),
                item(DayOfWeek.WEDNESDAY, 15, StatusBloco.CONCLUIDO)));
        FeedbackSemanal w45 = semana(2026, 45, List.of(
                item(DayOfWeek.MONDAY, 7, StatusBloco.INTERROMPIDO),
                item(DayOfWeek.WEDNESDAY, 17, StatusBloco.INTERROMPIDO)));
        FeedbackSemanal w46 = semana(2026, 46, List.of(
                item(DayOfWeek.MONDAY, 11, StatusBloco.NAO_ACONTECEU),
                item(DayOfWeek.WEDNESDAY, 13, StatusBloco.NAO_ACONTECEU)));
        FeedbackSemanal w47 = semana(2026, 47, List.of(
                item(DayOfWeek.FRIDAY, 19, StatusBloco.NAO_ACONTECEU)));
        FeedbackSemanal w48AtualIgnorada = semana(2026, 48, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.CONCLUIDO)));
        FeedbackSemanal w49FuturaIgnorada = semana(2026, 49, List.of(
                item(DayOfWeek.FRIDAY, 21, StatusBloco.CONCLUIDO)));

        // Lista propositalmente desordenada para garantir determinismo sem mutação da entrada
        List<FeedbackSemanal> historicoOriginal = new ArrayList<>(List.of(
                w49FuturaIgnorada, w46, w43ForaJanela, w48AtualIgnorada, w45, w47, w44));
        List<FeedbackSemanal> snapshot = List.copyOf(historicoOriginal);

        Map<FaixaHorario, ConfiabilidadeFaixa> mapa = service.calcularPorFaixa(
                new Semana(2026, 48, List.of(), List.of()), historicoOriginal);

        assertThat(historicoOriginal).isEqualTo(snapshot);
        assertThatThrownBy(mapa::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(mapa).containsKeys(segManha, quaTarde, sexNoite);

        // segManha: W44(0.0) + W45(0.5) + W46(0.0) => 3 amostras suficientes, média 0.5/3 < 0.50 -> baixa
        ConfiabilidadeFaixa confSegManha = mapa.get(segManha);
        assertThat(confSegManha.amostras()).isEqualTo(3);
        assertThat(confSegManha.indice()).isCloseTo(0.5 / 3.0, within(1e-9));
        assertThat(confSegManha.amostrasSuficientes()).isTrue();
        assertThat(confSegManha.baixaConfiabilidade()).isTrue();
        assertThat(service.ehBaixaConfiabilidade(confSegManha)).isTrue();

        // quaTarde: W44(1.0) + W45(0.5) + W46(0.0) => 3 amostras suficientes, média 0.50 (no limite -> NAO baixa)
        ConfiabilidadeFaixa confQuaTarde = mapa.get(quaTarde);
        assertThat(confQuaTarde.amostras()).isEqualTo(3);
        assertThat(confQuaTarde.indice()).isCloseTo(0.50, within(1e-9));
        assertThat(confQuaTarde.amostrasSuficientes()).isTrue();
        assertThat(confQuaTarde.baixaConfiabilidade()).isFalse();
        assertThat(service.ehBaixaConfiabilidade(confQuaTarde)).isFalse();

        // sexNoite: apenas W47(0.0) na janela => 1 amostra (< minimo 3 -> insuficiente e NAO baixa!)
        ConfiabilidadeFaixa confSexNoite = mapa.get(sexNoite);
        assertThat(confSexNoite.amostras()).isEqualTo(1);
        assertThat(confSexNoite.indice()).isEqualTo(0.0);
        assertThat(confSexNoite.amostrasSuficientes()).isFalse();
        assertThat(confSexNoite.baixaConfiabilidade()).isFalse();
        assertThat(service.ehBaixaConfiabilidade(confSexNoite)).isFalse();
    }

    @Test
    void devePermitirQueFaixaRuimMelhoreQuandoSemanasAntigasSairemDaJanelaECarregarDoDisco()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-disco");
        Path dirFeedback = dadosTemp.resolve("feedback");
        Files.createDirectories(dirFeedback);

        FeedbackRepository repo = new MarkdownFeedbackRepository();
        ConfiabilidadeService service = new ConfiabilidadeService(repo, dadosTemp, 3, 3, 0.50);

        FaixaHorario segManha = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);

        // Sem feedback no disco => mapa vazio sem erro
        Semana semanaAlvoW48 = new Semana(2026, 48, List.of(), List.of());
        assertThat(service.calcularPorFaixa(semanaAlvoW48)).isEmpty();
        assertThat(service.calcularConfiabilidades(semanaAlvoW48)).isEmpty();

        // W45, W46, W47 com falhas na segunda de manhã => em W48 fica abaixo de 0.50
        repo.salvar(dirFeedback.resolve("2026-W45.md"), semana(2026, 45, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.NAO_ACONTECEU))));
        repo.salvar(dirFeedback.resolve("2026-W46.md"), semana(2026, 46, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.NAO_ACONTECEU))));
        repo.salvar(dirFeedback.resolve("2026-W47.md"), semana(2026, 47, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.NAO_ACONTECEU))));
        Files.writeString(dirFeedback.resolve("anotacoes.md"), "# Notas avulsas\n", StandardCharsets.UTF_8);

        Map<FaixaHorario, ConfiabilidadeFaixa> emW48 = service.calcularPorFaixa(semanaAlvoW48);
        assertThat(emW48.get(segManha).amostrasSuficientes()).isTrue();
        assertThat(emW48.get(segManha).baixaConfiabilidade()).isTrue();
        assertThat(emW48.get(segManha).indice()).isEqualTo(0.0);

        // W48, W49 e W50 passam a ter conclusões; em W50 (janela W47..W49) sai da baixa confiabilidade
        repo.salvar(dirFeedback.resolve("2026-W48.md"), semana(2026, 48, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.CONCLUIDO))));
        repo.salvar(dirFeedback.resolve("2026-W49.md"), semana(2026, 49, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.CONCLUIDO))));
        repo.salvar(dirFeedback.resolve("2026-W50.md"), semana(2026, 50, List.of(
                item(DayOfWeek.MONDAY, 8, StatusBloco.CONCLUIDO))));

        Map<FaixaHorario, ConfiabilidadeFaixa> emW50 = service.calcularPorFaixa(
                new Semana(2026, 50, List.of(), List.of()));
        assertThat(emW50.get(segManha).indice()).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(emW50.get(segManha).baixaConfiabilidade()).isFalse();

        // Em 2027-W01 a ordenação cruza a virada do ano e enxerga [W48, W49, W50] = 1.0
        Map<FaixaHorario, ConfiabilidadeFaixa> em2027W01 = service.calcularPorFaixa(
                new Semana(2027, 1, List.of(), List.of()));
        assertThat(em2027W01.get(segManha).indice()).isEqualTo(1.0);
        assertThat(em2027W01.get(segManha).baixaConfiabilidade()).isFalse();

        // Feedback inválido no histórico falha explicitamente com DadosInvalidosException
        Files.writeString(dirFeedback.resolve("2026-W51.md"), "conteudo invalido\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.calcularPorFaixa(
                        new Semana(2027, 2, List.of(), List.of())))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("2026-W51.md");
    }

    private static FeedbackSemanal semana(int ano, int iso, List<ItemFeedback> itens) {
        return new FeedbackSemanal(ano, iso, itens, List.of());
    }

    private static ItemFeedback item(DayOfWeek dia, int horaInicio, StatusBloco status) {
        return new ItemFeedback(
                dia, LocalTime.of(horaInicio, 0), null, "Estudo (" + status + ")", status, null);
    }
}
