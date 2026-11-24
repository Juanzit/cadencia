package dev.cadencia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.StatusBloco;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownFeedbackRepositoryTest {

    private final FeedbackRepository repository = new MarkdownFeedbackRepository();

    @TempDir
    Path tempDir;

    @Test
    void deveGravarECarregarComRoundTripEIdempotenciaByteAByte() throws IOException {
        Path destino = tempDir.resolve("dados/feedback/2026-W48.md");
        FeedbackSemanal original = new FeedbackSemanal(
                2026,
                48,
                List.of(
                        new ItemFeedback(
                                DayOfWeek.MONDAY,
                                LocalTime.of(7, 0),
                                LocalTime.of(7, 25),
                                "Java Core: Streams (1/4)",
                                StatusBloco.CONCLUIDO,
                                null),
                        new ItemFeedback(
                                DayOfWeek.TUESDAY,
                                LocalTime.of(15, 0),
                                null,
                                "Flashcards Java (1/4)",
                                StatusBloco.INTERROMPIDO,
                                "parei no card 12"),
                        new ItemFeedback(
                                DayOfWeek.WEDNESDAY,
                                LocalTime.of(19, 0),
                                LocalTime.of(19, 30),
                                "Spring Boot (1/5)",
                                StatusBloco.NAO_ACONTECEU,
                                null),
                        new ItemFeedback(
                                DayOfWeek.THURSDAY,
                                LocalTime.of(8, 0),
                                LocalTime.of(8, 25),
                                "SQL (1/3)",
                                StatusBloco.CONCLUIDO,
                                "resumo | parte A"),
                        new ItemFeedback(
                                DayOfWeek.FRIDAY,
                                LocalTime.of(14, 0),
                                LocalTime.of(14, 25),
                                "JPA | Consultas (1/4)",
                                StatusBloco.CONCLUIDO,
                                null),
                        new ItemFeedback(
                                DayOfWeek.SATURDAY,
                                LocalTime.of(10, 0),
                                LocalTime.of(10, 25),
                                "Sábado revisão",
                                StatusBloco.INTERROMPIDO,
                                "capítulo 3"),
                        new ItemFeedback(
                                DayOfWeek.SUNDAY,
                                LocalTime.of(18, 30),
                                LocalTime.of(19, 0),
                                "Domingo leitura",
                                StatusBloco.NAO_ACONTECEU,
                                null)),
                List.of("Semana corrida; plantão foi tranquilo.", "Ajustar sono na quarta."));

        Path salvo = repository.salvar(destino, original);
        byte[] primeiraGravacao = Files.readAllBytes(salvo);
        String conteudo = new String(primeiraGravacao, StandardCharsets.UTF_8);

        assertThat(salvo).isEqualTo(destino);
        assertThat(conteudo)
                .doesNotContain("\r")
                .endsWith("\n")
                .contains("# Feedback 2026-W48\n")
                .contains("## Blocos e estudos\n")
                .contains("- [x] seg 07:00–07:25 · Java Core: Streams (1/4) | CONCLUIDO\n")
                .contains("- [ ] ter 15:00 · Flashcards Java (1/4) | INTERROMPIDO | parei no card 12\n")
                .contains("- [ ] sáb 10:00–10:25 · Sábado revisão | INTERROMPIDO | capítulo 3\n")
                .contains("## Notas livres\n- Semana corrida; plantão foi tranquilo.\n- Ajustar sono na quarta.\n");

        FeedbackSemanal carregado = repository.carregar(salvo);
        assertThat(carregado).isEqualTo(original);

        repository.salvar(destino, carregado);
        byte[] segundaGravacao = Files.readAllBytes(destino);
        assertThat(segundaGravacao).isEqualTo(primeiraGravacao);
    }

    @Test
    void deveTratarNotasLivresVaziaUmaNotaEMultiplasNotasComRoundTripEIdempotenciaByteAByte()
            throws IOException {
        Path destinoVazia = tempDir.resolve("notas-vazia.md");
        FeedbackSemanal semNotas = new FeedbackSemanal(2026, 48, List.of(), List.of());

        repository.salvar(destinoVazia, semNotas);
        String markdownVazio = Files.readString(destinoVazia, StandardCharsets.UTF_8);
        assertThat(markdownVazio).isEqualTo("""
                # Feedback 2026-W48

                ## Blocos e estudos

                ## Notas livres
                - Nenhuma nota registrada.
                """);
        FeedbackSemanal semNotasLido = repository.carregar(destinoVazia);
        assertThat(semNotasLido.notasGerais()).isEmpty();
        assertThat(semNotasLido).isEqualTo(semNotas);

        byte[] bytesVazio1 = Files.readAllBytes(destinoVazia);
        repository.salvar(destinoVazia, semNotasLido);
        assertThat(Files.readAllBytes(destinoVazia)).isEqualTo(bytesVazio1);

        Path destinoUma = tempDir.resolve("notas-uma.md");
        FeedbackSemanal umaNota = new FeedbackSemanal(
                2026, 48, List.of(), List.of("Revisar arquitetura hexagonal."));

        repository.salvar(destinoUma, umaNota);
        String markdownUma = Files.readString(destinoUma, StandardCharsets.UTF_8);
        assertThat(markdownUma).isEqualTo("""
                # Feedback 2026-W48

                ## Blocos e estudos

                ## Notas livres
                - Revisar arquitetura hexagonal.
                """);
        FeedbackSemanal umaNotaLida = repository.carregar(destinoUma);
        assertThat(umaNotaLida.notasGerais()).containsExactly("Revisar arquitetura hexagonal.");
        assertThat(umaNotaLida).isEqualTo(umaNota);

        byte[] bytesUma1 = Files.readAllBytes(destinoUma);
        repository.salvar(destinoUma, umaNotaLida);
        assertThat(Files.readAllBytes(destinoUma)).isEqualTo(bytesUma1);

        Path destinoMultiplas = tempDir.resolve("notas-multiplas.md");
        FeedbackSemanal multiplasNotas = new FeedbackSemanal(
                2026,
                48,
                List.of(),
                List.of(
                        "Primeira observação da semana.",
                        "Segunda observação: manter pausas.",
                        "Terceira observação."));

        repository.salvar(destinoMultiplas, multiplasNotas);
        String markdownMultiplas = Files.readString(destinoMultiplas, StandardCharsets.UTF_8);
        assertThat(markdownMultiplas).isEqualTo("""
                # Feedback 2026-W48

                ## Blocos e estudos

                ## Notas livres
                - Primeira observação da semana.
                - Segunda observação: manter pausas.
                - Terceira observação.
                """);
        FeedbackSemanal multiplasLidas = repository.carregar(destinoMultiplas);
        assertThat(multiplasLidas.notasGerais()).containsExactly(
                "Primeira observação da semana.",
                "Segunda observação: manter pausas.",
                "Terceira observação.");
        assertThat(multiplasLidas).isEqualTo(multiplasNotas);

        byte[] bytesMultiplas1 = Files.readAllBytes(destinoMultiplas);
        repository.salvar(destinoMultiplas, multiplasLidas);
        assertThat(Files.readAllBytes(destinoMultiplas)).isEqualTo(bytesMultiplas1);
    }

    @Test
    void deveManterRoundTripParaFeedbackSemItensNemNotasEDescartarNotasEmBrancoAoSalvar()
            throws IOException {
        Path destinoVazio = tempDir.resolve("2026-W49.md");
        FeedbackSemanal vazio = new FeedbackSemanal(2026, 49, List.of(), List.of("   ", "Linha válida\r\nlinha 2"));

        repository.salvar(destinoVazio, vazio);
        FeedbackSemanal carregado = repository.carregar(destinoVazio);

        assertThat(carregado.ano()).isEqualTo(2026);
        assertThat(carregado.numeroIso()).isEqualTo(49);
        assertThat(carregado.itens()).isEmpty();
        assertThat(carregado.notasGerais()).containsExactly("Linha válida linha 2");

        byte[] bytesAntes = Files.readAllBytes(destinoVazio);
        repository.salvar(destinoVazio, carregado);
        assertThat(Files.readAllBytes(destinoVazio)).isEqualTo(bytesAntes);
    }

    @Test
    void deveAceitarFormatoCurtoDesignFormatoModeloEVariacoesDeHorarioEDia() throws IOException {
        Path arquivo = tempDir.resolve("2026-W50.md");
        String markdown = "\uFEFF# Feedback 2026-W50\r\n"
                + "- [x] seg 20:00 Java Collections (1/6) | CONCLUIDO\r\n"
                + "- [X] sab 08:00-08:30 · Revisão sab | CONCLUIDO |\r\n"
                + "- [ ] ter 15:00 · Flashcards Java (1/4) | INTERROMPIDO | parei no card 12\r\n"
                + "- [ ] qua 09:00 – 09:25 · - 2a aula de SQL | NAO_ACONTECEU |\r\n"
                + "- [ ] dom 11:00 - Aula extra | NAO_ACONTECEU\r\n"
                + "\r\n"
                + "## Notas livres\r\n"
                + "Observação manual no AppFlowy.\r\n";
        Files.writeString(arquivo, markdown, StandardCharsets.UTF_8);

        FeedbackSemanal carregado = repository.carregar(arquivo);

        assertThat(carregado.ano()).isEqualTo(2026);
        assertThat(carregado.numeroIso()).isEqualTo(50);
        assertThat(carregado.itens()).containsExactly(
                new ItemFeedback(
                        DayOfWeek.MONDAY,
                        LocalTime.of(20, 0),
                        null,
                        "Java Collections (1/6)",
                        StatusBloco.CONCLUIDO,
                        null),
                new ItemFeedback(
                        DayOfWeek.SATURDAY,
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 30),
                        "Revisão sab",
                        StatusBloco.CONCLUIDO,
                        null),
                new ItemFeedback(
                        DayOfWeek.TUESDAY,
                        LocalTime.of(15, 0),
                        null,
                        "Flashcards Java (1/4)",
                        StatusBloco.INTERROMPIDO,
                        "parei no card 12"),
                new ItemFeedback(
                        DayOfWeek.WEDNESDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 25),
                        "- 2a aula de SQL",
                        StatusBloco.NAO_ACONTECEU,
                        null),
                new ItemFeedback(
                        DayOfWeek.SUNDAY,
                        LocalTime.of(11, 0),
                        null,
                        "- Aula extra",
                        StatusBloco.NAO_ACONTECEU,
                        null));
        assertThat(carregado.notasGerais()).containsExactly("Observação manual no AppFlowy.");
    }

    @Test
    void deveCarregarSecaoExplicitaDeBlocosSemNotasLivres() throws IOException {
        Path arquivo = tempDir.resolve("2026-W51.md");
        Files.writeString(
                arquivo,
                "\n# Feedback 2026-W51\n\n## Blocos e estudos\n- [x] qui 14:00–14:25 · Testes JUnit | CONCLUIDO\n",
                StandardCharsets.UTF_8);

        FeedbackSemanal carregado = repository.carregar(arquivo);
        assertThat(carregado.itens()).hasSize(1);
        assertThat(carregado.notasGerais()).isEmpty();
    }

    @Test
    void deveReportarArquivoELinhaExataParaErrosDeCabecalhoSecaoEFormato() throws IOException {
        Path inexistente = tempDir.resolve("nao-existe.md");
        assertThatThrownBy(() -> repository.carregar(inexistente))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(inexistente.toString());

        assertarErroNaLinha("vazio.md", "\n  \n", "linha 1", "vazio");
        assertarErroNaLinha("cabecalho-errado.md", "# Semana 2026-W48\n", "linha 1", "cabeçalho inválido");
        assertarErroNaLinha("ano-invalido.md", "# Feedback 1999-W01\n", "linha 1", "ano 1999 inválido");
        assertarErroNaLinha("semana-zero.md", "# Feedback 2026-W00\n", "linha 1", "W00");
        assertarErroNaLinha("semana-54.md", "# Feedback 2026-W54\n", "linha 1", "W54");

        assertarErroNaLinha(
                "secao-desconhecida.md",
                "# Feedback 2026-W48\n\n## Resumo\n",
                "linha 3",
                "desconhecida");
        assertarErroNaLinha(
                "blocos-duplicados.md",
                "# Feedback 2026-W48\n## Blocos e estudos\n## Blocos e estudos\n",
                "linha 3",
                "duplicada");
        assertarErroNaLinha(
                "blocos-apos-notas.md",
                "# Feedback 2026-W48\n## Notas livres\n## Blocos e estudos\n",
                "linha 3",
                "fora de ordem");
        assertarErroNaLinha(
                "blocos-apos-item.md",
                "# Feedback 2026-W48\n- [x] seg 07:00 Estudo | CONCLUIDO\n## Blocos e estudos\n",
                "linha 3",
                "fora de ordem");
        assertarErroNaLinha(
                "notas-duplicadas.md",
                "# Feedback 2026-W48\n## Notas livres\nOk\n## Notas livres\n",
                "linha 4",
                "duplicada");
    }

    @Test
    void deveReportarArquivoELinhaExataParaErrosNosItensEConsistenciaCheckboxStatus() throws IOException {
        assertarErroNaLinha(
                "sem-checkbox.md",
                "# Feedback 2026-W48\n## Blocos e estudos\nseg 07:00 Estudo | CONCLUIDO\n",
                "linha 3",
                "formato de item inválido");
        assertarErroNaLinha(
                "checkbox-invalido.md",
                "# Feedback 2026-W48\n- [o] seg 07:00 Estudo | CONCLUIDO\n",
                "linha 2",
                "marcador \"[o]\" inválido");
        assertarErroNaLinha(
                "item-vazio.md",
                "# Feedback 2026-W48\n- [ ]   \n",
                "linha 2",
                "vazio após o checkbox");
        assertarErroNaLinha(
                "sem-status.md",
                "# Feedback 2026-W48\n- [x] seg 07:00 Estudo\n",
                "linha 2",
                "status ausente");
        assertarErroNaLinha(
                "pipes-em-excesso.md",
                "# Feedback 2026-W48\n- [ ] seg 07:00 Estudo | INTERROMPIDO | nota | extra\n",
                "linha 2",
                "em excesso");
        assertarErroNaLinha(
                "status-alias-proibido.md",
                "# Feedback 2026-W48\n- [ ] seg 07:00 Estudo | PARCIAL\n",
                "linha 2",
                "status \"PARCIAL\" inválido");
        assertarErroNaLinha(
                "inconsistencia-x-interrompido.md",
                "# Feedback 2026-W48\n- [x] seg 07:00 Estudo | INTERROMPIDO\n",
                "linha 2",
                "exige status CONCLUIDO");
        assertarErroNaLinha(
                "inconsistencia-vazio-concluido.md",
                "# Feedback 2026-W48\n\n## Blocos e estudos\n\n- [ ] seg 07:00 Estudo | CONCLUIDO\n",
                "linha 5",
                "exige checkbox marcado como [x]");
        assertarErroNaLinha(
                "somente-dia.md",
                "# Feedback 2026-W48\n- [ ] seg | NAO_ACONTECEU\n",
                "linha 2",
                "informe dia, horário e título");
        assertarErroNaLinha(
                "dia-invalido.md",
                "# Feedback 2026-W48\n- [ ] segunda 07:00 Estudo | NAO_ACONTECEU\n",
                "linha 2",
                "dia da semana \"segunda\" inválido");
        assertarErroNaLinha(
                "hora-formato-curto.md",
                "# Feedback 2026-W48\n- [ ] seg 7:00 Estudo | NAO_ACONTECEU\n",
                "linha 2",
                "horário de início \"7:00\" inválido");
        assertarErroNaLinha(
                "hora-inexistente.md",
                "# Feedback 2026-W48\n- [ ] seg 25:00 Estudo | NAO_ACONTECEU\n",
                "linha 2",
                "horário de início \"25:00\" inválido");
        assertarErroNaLinha(
                "fim-invalido.md",
                "# Feedback 2026-W48\n- [ ] seg 07:00–24:60 Estudo | NAO_ACONTECEU\n",
                "linha 2",
                "horário de fim \"24:60\" inválido");
        assertarErroNaLinha(
                "fim-menor-igual-inicio.md",
                "# Feedback 2026-W48\n- [ ] seg 10:00–09:00 Estudo | NAO_ACONTECEU\n",
                "linha 2",
                "estritamente posterior ao início");
        assertarErroNaLinha(
                "fim-igual-inicio.md",
                "# Feedback 2026-W48\n- [ ] seg 10:00–10:00 Estudo | NAO_ACONTECEU\n",
                "linha 2",
                "estritamente posterior ao início");
        assertarErroNaLinha(
                "titulo-ausente.md",
                "# Feedback 2026-W48\n- [ ] seg 07:00–07:25 | NAO_ACONTECEU\n",
                "linha 2",
                "título do bloco/estudo ausente");
        assertarErroNaLinha(
                "titulo-so-ponto.md",
                "# Feedback 2026-W48\n- [ ] seg 07:00 ·   | NAO_ACONTECEU\n",
                "linha 2",
                "título do bloco/estudo ausente");
    }

    @Test
    void deveImplementarCarregarSeExistirEListarHistoricoEmOrdemCronologica() throws IOException {
        Path feedbackDir = tempDir.resolve("feedback");
        assertThat(repository.carregarSeExistir(feedbackDir.resolve("2026-W48.md"))).isEmpty();
        assertThat(repository.listarHistorico(feedbackDir)).isEmpty();

        Path arquivoComoDiretorio = tempDir.resolve("nao-diretorio.md");
        Files.writeString(arquivoComoDiretorio, "# Feedback 2026-W48\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.listarHistorico(arquivoComoDiretorio))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("não é um diretório");

        Files.createDirectories(feedbackDir.resolve("subpasta"));
        Files.writeString(feedbackDir.resolve(".gitkeep"), "", StandardCharsets.UTF_8);
        Files.writeString(
                feedbackDir.resolve("anotacoes.md"),
                "# Anotações soltas\nTexto livre sem formato de feedback semanal.\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                feedbackDir.resolve("rascunho-2026-W48.md"),
                "Arquivo auxiliar não versionado como feedback oficial.\n",
                StandardCharsets.UTF_8);

        repository.salvar(
                feedbackDir.resolve("2027-W01.md"),
                new FeedbackSemanal(2027, 1, List.of(), List.of()));
        repository.salvar(
                feedbackDir.resolve("2026-W49.md"),
                new FeedbackSemanal(2026, 49, List.of(), List.of()));
        repository.salvar(
                feedbackDir.resolve("2025-W52.md"),
                new FeedbackSemanal(2025, 52, List.of(), List.of()));
        repository.salvar(
                feedbackDir.resolve("2026-W02.md"),
                new FeedbackSemanal(2026, 2, List.of(), List.of()));

        assertThat(repository.carregarSeExistir(feedbackDir.resolve("2026-W49.md")))
                .isPresent()
                .get()
                .extracting(FeedbackSemanal::ano, FeedbackSemanal::numeroIso)
                .containsExactly(2026, 49);

        List<FeedbackSemanal> historico = repository.listarHistorico(feedbackDir);
        assertThat(historico)
                .extracting(FeedbackSemanal::ano, FeedbackSemanal::numeroIso)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2025, 52),
                        org.assertj.core.groups.Tuple.tuple(2026, 2),
                        org.assertj.core.groups.Tuple.tuple(2026, 49),
                        org.assertj.core.groups.Tuple.tuple(2027, 1));
        assertThatThrownBy(() -> historico.add(new FeedbackSemanal(2028, 1, List.of(), List.of())))
                .isInstanceOf(UnsupportedOperationException.class);

        Path corrompido = feedbackDir.resolve("2026-W50.md");
        Files.writeString(corrompido, "# Feedback 2026-W50\n- [ ] seg 07:00 Estudo | INVALIDO\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.listarHistorico(feedbackDir))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(corrompido.toString())
                .hasMessageContaining("linha 2");
    }

    @Test
    void deveValidarParametrosNulosEPropagarFalhaDeGravacao() throws IOException {
        assertThatNullPointerException().isThrownBy(() -> repository.carregar(null));
        assertThatNullPointerException().isThrownBy(() -> repository.carregarSeExistir(null));
        assertThatNullPointerException().isThrownBy(() -> repository.listarHistorico(null));
        assertThatNullPointerException().isThrownBy(
                () -> repository.salvar(null, new FeedbackSemanal(2026, 48, List.of(), List.of())));
        assertThatNullPointerException().isThrownBy(
                () -> repository.salvar(tempDir.resolve("a.md"), null));

        Path diretorioExistente = tempDir.resolve("pasta-ocupada");
        Files.createDirectories(diretorioExistente);
        FeedbackSemanal feedback = new FeedbackSemanal(2026, 48, List.of(), List.of());
        assertThatThrownBy(() -> repository.salvar(diretorioExistente, feedback))
                .isInstanceOf(UncheckedIOException.class);
    }

    private void assertarErroNaLinha(
            String nomeArquivo, String conteudo, String trechoLinha, String trechoDetalhe)
            throws IOException {
        Path arquivo = tempDir.resolve(nomeArquivo);
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(arquivo))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(arquivo.toString())
                .hasMessageContaining(trechoLinha)
                .hasMessageContaining(trechoDetalhe);
    }
}
