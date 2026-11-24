package dev.cadencia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.PeriodoDia;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.StatusBloco;
import dev.cadencia.exportacao.MarkdownPlanoSemanalRepository;
import dev.cadencia.exportacao.PlanoSemanalGerado;
import dev.cadencia.exportacao.PlanoSemanalRepository;
import dev.cadencia.exportacao.PlanoSemanalService;
import dev.cadencia.exportacao.strategies.MarkdownSemanalExportador;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.YamlBacklogRepository;
import dev.cadencia.planejamento.strategies.ConfiabilidadeAlocadorStrategy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FeedbackConfiabilidadeCasosDeBordaTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO_W48 = Clock.fixed(
            LocalDate.of(2026, 11, 23).atTime(9, 0).atZone(SAO_PAULO).toInstant(),
            SAO_PAULO);

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private static ObjectMapper yamlMapper;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void configurarValidacao() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @AfterAll
    static void fecharValidacao() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void caso1PoucasAmostrasDeveManterComportamentoPadraoDaFase3SemRestringirBlocosProfundos()
            throws IOException {
        Path dadosTemp = prepararAmbienteBasico("caso-1-poucas-amostras");
        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();

        // Apenas 2 semanas (< mínimo padrão 3) com falha na segunda de manhã
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 46, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 47, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);

        ConfiabilidadeService confiabilidadeService = new ConfiabilidadeService(feedbackRepo, dadosTemp);
        Map<FaixaHorario, ConfiabilidadeFaixa> mapa = confiabilidadeService.calcularPorFaixa(
                new Semana(2026, 48, List.of(), List.of()));
        FaixaHorario segManha = new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA);

        assertThat(mapa.get(segManha).amostras()).isEqualTo(2);
        assertThat(mapa.get(segManha).amostrasSuficientes()).isFalse();
        assertThat(mapa.get(segManha).baixaConfiabilidade()).isFalse();

        PlanoSemanalGerado gerado = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        assertThat(markdown)
                .contains("| 07:00–07:25 | **Bloco profundo** | Java Core (1/2) |")
                .contains("| 07:25–07:50 | **Bloco profundo** | Java Core (2/2) |")
                .doesNotContain("baixa confiabilidade");
    }

    @Test
    void caso2FaixaRuimComTresSemanasEDepoisMelhoraComOTempoNaJanelaDeQuatroSemanas()
            throws IOException {
        Path dadosTemp = prepararAmbienteBasico("caso-2-evolucao");
        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();

        // W45, W46, W47: segunda manhã falha => em W48 (janela 4) fica degradada
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 45, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 46, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 47, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);

        PlanoSemanalGerado w48 = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23));
        String mdW48 = Files.readString(w48.arquivoSaida(), StandardCharsets.UTF_8);
        assertThat(mdW48).contains(
                "| 07:00–07:25 | **Bloco profundo** | Flashcards Java (1/1) |",
                "| 07:25–07:50 | **Bloco profundo** | Testes JUnit (1/1) |",
                "| 13:10–13:35 | **Bloco profundo** | Java Core (1/2) |",
                "| 13:35–14:00 | **Bloco profundo** | Java Core (2/2) |",
                "Faixa segunda-feira (manhã) com baixa confiabilidade (0%, 3 amostra(s)): blocos profundos aceitarão apenas tarefas interrompíveis (INTERROMPIVEL).");

        // W48, W49 e W50 recuperam o histórico de segunda de manhã (CONCLUIDO = 1.0)
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 48, DayOfWeek.MONDAY, 7, StatusBloco.CONCLUIDO, null);
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 49, DayOfWeek.MONDAY, 7, StatusBloco.CONCLUIDO, null);
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 50, DayOfWeek.MONDAY, 7, StatusBloco.CONCLUIDO, null);

        // Na W51 (janela das últimas 4 semanas: W47=0.0, W48=1.0, W49=1.0, W50=1.0 => média 0.75 >= 0.50)
        ConfiabilidadeService confiabilidadeService = new ConfiabilidadeService(feedbackRepo, dadosTemp);
        ConfiabilidadeFaixa segManhaW51 = confiabilidadeService
                .calcularPorFaixa(new Semana(2026, 51, List.of(), List.of()))
                .get(new FaixaHorario(DayOfWeek.MONDAY, PeriodoDia.MANHA));
        assertThat(segManhaW51.amostras()).isEqualTo(4);
        assertThat(segManhaW51.indice()).isEqualTo(0.75);
        assertThat(segManhaW51.baixaConfiabilidade()).isFalse();

        PlanoSemanalGerado w51 = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 12, 14));
        String mdW51 = Files.readString(w51.arquivoSaida(), StandardCharsets.UTF_8);
        assertThat(mdW51)
                .contains(
                        "| 07:00–07:25 | **Bloco profundo** | Java Core (1/2) |",
                        "| 07:25–07:50 | **Bloco profundo** | Java Core (2/2) |")
                .doesNotContain("baixa confiabilidade");
    }

    @Test
    void caso3NotaDeRetomadaDevePersistirAteConclusaoEIgnorarSemanasFuturas() throws IOException {
        Path dadosTemp = prepararAmbienteBasico("caso-3-retomada");
        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();

        feedbackRepo.salvar(
                dadosTemp.resolve("feedback/2026-W46.md"),
                new FeedbackSemanal(
                        2026,
                        46,
                        List.of(new ItemFeedback(
                                DayOfWeek.MONDAY,
                                LocalTime.of(13, 10),
                                LocalTime.of(13, 35),
                                "Java Core (1/2)",
                                StatusBloco.INTERROMPIDO,
                                "parei no exercicio 4")),
                        List.of()));

        // Na W47 a tarefa não aconteceu sem nota nova -> nota de W46 continua vigente; W49 futura não vaza
        feedbackRepo.salvar(
                dadosTemp.resolve("feedback/2026-W47.md"),
                new FeedbackSemanal(
                        2026,
                        47,
                        List.of(new ItemFeedback(
                                DayOfWeek.TUESDAY,
                                LocalTime.of(8, 0),
                                null,
                                "Java Core",
                                StatusBloco.NAO_ACONTECEU,
                                null)),
                        List.of()));
        feedbackRepo.salvar(
                dadosTemp.resolve("feedback/2026-W49.md"),
                new FeedbackSemanal(
                        2026,
                        49,
                        List.of(new ItemFeedback(
                                DayOfWeek.WEDNESDAY,
                                LocalTime.of(9, 0),
                                null,
                                "Flashcards Java (1/1)",
                                StatusBloco.INTERROMPIDO,
                                "nota futura ignorada")),
                        List.of()));

        String mdW48 = Files.readString(
                executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23)).arquivoSaida(),
                StandardCharsets.UTF_8);
        assertThat(mdW48)
                .contains("- [ ] seg 07:00–07:25 · Java Core (1/2) — retomar: parei no exercicio 4\n")
                .contains("- [ ] seg 07:25–07:50 · Java Core (2/2)\n")
                .doesNotContain("nota futura ignorada");
        assertThat(mdW48.split("— retomar:", -1)).hasSize(2);

        // Na W48 Java Core é concluída (mesmo com texto residual); na W49 a nota deixa de reaparecer
        feedbackRepo.salvar(
                dadosTemp.resolve("feedback/2026-W48.md"),
                new FeedbackSemanal(
                        2026,
                        48,
                        List.of(new ItemFeedback(
                                DayOfWeek.MONDAY,
                                LocalTime.of(7, 0),
                                LocalTime.of(7, 25),
                                "Java Core (1/2)",
                                StatusBloco.CONCLUIDO,
                                "texto residual ignorado")),
                        List.of()));

        String mdW49 = Files.readString(
                executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 30)).arquivoSaida(),
                StandardCharsets.UTF_8);
        assertThat(mdW49)
                .contains("- [ ] seg 07:00–07:25 · Java Core (1/2)\n")
                .doesNotContain("parei no exercicio 4")
                .doesNotContain("texto residual ignorado");
    }

    @Test
    void caso4FeedbackInvalidoDeveFalharComArquivoELinhaExataNaLeituraENoPlanejamento() throws IOException {
        Path dadosTemp = prepararAmbienteBasico("caso-4-feedback-invalido");
        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();

        Path statusInvalido = dadosTemp.resolve("feedback/2026-W47.md");
        Files.createDirectories(statusInvalido.getParent());
        Files.writeString(statusInvalido, """
                # Feedback 2026-W47

                ## Blocos e estudos
                - [ ] seg 07:00–07:25 · Java Core (1/2) | PARCIAL
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> feedbackRepo.carregar(statusInvalido))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("2026-W47.md")
                .hasMessageContaining("linha 4");

        assertThatThrownBy(() -> executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("2026-W47.md")
                .hasMessageContaining("linha 4");

        Path horarioInvertido = dadosTemp.resolve("feedback/2026-W48.md");
        Files.writeString(horarioInvertido, """
                # Feedback 2026-W48
                - [ ] ter 11:00–10:00 · Estudo | INTERROMPIDO | parei
                """, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> feedbackRepo.carregar(horarioInvertido))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("2026-W48.md")
                .hasMessageContaining("linha 2");
    }

    @Test
    void caso5SemanaSemFeedbackDeveGerarPlanoEModeloIdempotentesSemErros() throws IOException {
        Path dadosTemp = prepararAmbienteBasico("caso-5-sem-feedback");
        Files.writeString(dadosTemp.resolve("backlog.yaml"), """
                tarefas:
                  - id: "java-core"
                    titulo: "Java Core"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: "junit"
                    titulo: "Testes JUnit"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO, INTERROMPIVEL]
                    prioridade: 2
                """, StandardCharsets.UTF_8);

        // Garante ausência inicial de feedback/ e também tolerância a arquivos auxiliares não-AAAA-Wnn.md
        PlanoSemanalGerado primeiro = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23));
        byte[] bytesPrimeiro = Files.readAllBytes(primeiro.arquivoSaida());

        Files.createDirectories(dadosTemp.resolve("feedback"));
        Files.writeString(dadosTemp.resolve("feedback/anotacoes.md"), "# Anotações pessoais\n", StandardCharsets.UTF_8);

        PlanoSemanalGerado segundo = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23));
        byte[] bytesSegundo = Files.readAllBytes(segundo.arquivoSaida());

        assertThat(bytesSegundo).isEqualTo(bytesPrimeiro);
        String markdown = new String(bytesPrimeiro, StandardCharsets.UTF_8);
        assertThat(markdown)
                .doesNotContain("\r")
                .contains("| 07:00–07:25 | **Bloco profundo** | Java Core (1/2) |")
                .contains("## Avisos\n- Nenhum aviso.");
    }

    @Test
    void caso6DeveValidarFeedbackExemploESimularSemana2026W48ComExemplosDoRepositorio()
            throws IOException {
        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();
        FeedbackSemanal exemplo = feedbackRepo.carregar(Path.of("exemplos/feedback.exemplo.md"));
        assertThat(exemplo.ano()).isEqualTo(2026);
        assertThat(exemplo.numeroIso()).isEqualTo(47);
        assertThat(exemplo.itens()).isNotEmpty();
        assertThat(exemplo.notasGerais()).isNotEmpty();

        Path dadosTemp = tempDir.resolve("caso-6-exemplos-completos");
        Files.createDirectories(dadosTemp.resolve("feedback"));
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));
        Files.copy(Path.of("exemplos/backlog.exemplo.yaml"), dadosTemp.resolve("backlog.yaml"));
        Files.copy(Path.of("exemplos/feedback.exemplo.md"), dadosTemp.resolve("feedback/2026-W47.md"));

        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 45, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);
        salvarFeedbackDia(feedbackRepo, dadosTemp, 2026, 46, DayOfWeek.MONDAY, 7, StatusBloco.NAO_ACONTECEU, null);

        PlanoSemanalGerado primeiro = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23));
        PlanoSemanalGerado segundo = executarPlanoSemanal(dadosTemp, LocalDate.of(2026, 11, 23));

        byte[] bytes1 = Files.readAllBytes(primeiro.arquivoSaida());
        byte[] bytes2 = Files.readAllBytes(segundo.arquivoSaida());
        assertThat(bytes2).isEqualTo(bytes1);

        String mdW48 = new String(bytes1, StandardCharsets.UTF_8);
        assertThat(mdW48)
                .doesNotContain("\r")
                .contains("| 07:00–07:25 | **Bloco profundo** | Testes com JUnit 5, AssertJ e Mockito (3/4) |")
                .contains("| 13:10–13:35 | **Bloco profundo** | Java Core: Streams, Lambdas e Records (1/4) |")
                .contains("- [ ] seg 13:35–14:00 · Java Collections e Complexidade (4/6) — retomar: parei no exercício 7 de TreeMap")
                .contains("Faixa segunda-feira (manhã) com baixa confiabilidade (0%, 3 amostra(s)): blocos profundos aceitarão apenas tarefas interrompíveis (INTERROMPIVEL).");
        assertThat(mdW48.split("— retomar:", -1)).hasSize(2);
    }

    private Path prepararAmbienteBasico(String subpasta) throws IOException {
        Path dadosTemp = tempDir.resolve(subpasta);
        Files.createDirectories(dadosTemp);
        Files.writeString(dadosTemp.resolve("agenda.yaml"), """
                jornada:
                  inicio: "07:00"
                  fim: "15:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "09:00"
                    fim: "13:00"
                    tipo: REUNIAO
                    titulo: "Aula"
                  - dia: TERCA
                    inicio: "07:00"
                    fim: "15:00"
                    tipo: REUNIAO
                    titulo: "Dia ocupado"
                  - dia: QUARTA
                    inicio: "07:00"
                    fim: "15:00"
                    tipo: REUNIAO
                    titulo: "Dia ocupado"
                  - dia: QUINTA
                    inicio: "07:00"
                    fim: "15:00"
                    tipo: REUNIAO
                    titulo: "Dia ocupado"
                  - dia: SEXTA
                    inicio: "07:00"
                    fim: "15:00"
                    tipo: REUNIAO
                    titulo: "Dia ocupado"
                  - dia: SABADO
                    inicio: "07:00"
                    fim: "15:00"
                    tipo: REUNIAO
                    titulo: "Dia ocupado"
                  - dia: DOMINGO
                    inicio: "07:00"
                    fim: "15:00"
                    tipo: REUNIAO
                    titulo: "Dia ocupado"
                """, StandardCharsets.UTF_8);
        Files.writeString(dadosTemp.resolve("backlog.yaml"), """
                tarefas:
                  - id: "java-core"
                    titulo: "Java Core"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: "flashcards"
                    titulo: "Flashcards Java"
                    duracao_total_min: 25
                    tipos_aceitos: [INTERROMPIVEL]
                    prioridade: 2
                  - id: "junit"
                    titulo: "Testes JUnit"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO, INTERROMPIVEL]
                    prioridade: 3
                """, StandardCharsets.UTF_8);
        return dadosTemp;
    }

    private static void salvarFeedbackDia(
            FeedbackRepository repo,
            Path dadosTemp,
            int ano,
            int iso,
            DayOfWeek dia,
            int horaInicio,
            StatusBloco status,
            String nota) {
        repo.salvar(
                dadosTemp.resolve(String.format("feedback/%04d-W%02d.md", ano, iso)),
                new FeedbackSemanal(
                        ano,
                        iso,
                        List.of(new ItemFeedback(
                                dia,
                                LocalTime.of(horaInicio, 0),
                                LocalTime.of(horaInicio, 25),
                                "Estudo (" + iso + ")",
                                status,
                                nota)),
                        List.of()));
    }

    private static PlanoSemanalGerado executarPlanoSemanal(Path dadosTemp, LocalDate dataReferencia) {
        AgendaRepository agendaRepo = new YamlAgendaRepository(yamlMapper, validator);
        AgendaService agendaService = new AgendaService(agendaRepo, RELOGIO_W48);
        BacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, validator);
        PlanejamentoService planejamentoService = new PlanejamentoService(new ConfiabilidadeAlocadorStrategy());
        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();
        ConfiabilidadeService confiabilidadeService = new ConfiabilidadeService(feedbackRepo, dadosTemp);
        FeedbackService feedbackService = new FeedbackService(
                agendaService, backlogRepo, planejamentoService, feedbackRepo, confiabilidadeService, dadosTemp);
        MarkdownSemanalExportador exportador = new MarkdownSemanalExportador();
        PlanoSemanalRepository planoRepo = new MarkdownPlanoSemanalRepository();

        PlanoSemanalService service = new PlanoSemanalService(
                agendaService,
                backlogRepo,
                planejamentoService,
                feedbackService,
                confiabilidadeService,
                exportador,
                planoRepo,
                dadosTemp);
        return service.gerarEExportar(null, null, dataReferencia);
    }
}
