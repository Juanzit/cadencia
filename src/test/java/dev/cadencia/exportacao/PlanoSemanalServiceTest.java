package dev.cadencia.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.StatusBloco;
import dev.cadencia.exportacao.strategies.MarkdownSemanalExportador;
import dev.cadencia.feedback.FeedbackRepository;
import dev.cadencia.feedback.FeedbackService;
import dev.cadencia.feedback.MarkdownFeedbackRepository;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.YamlBacklogRepository;
import dev.cadencia.planejamento.strategies.PrioridadeAlocadorStrategy;
import jakarta.validation.Validation;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanoSemanalServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO_W48 = Clock.fixed(
            LocalDate.of(2026, 11, 23).atTime(9, 0).atZone(SAO_PAULO).toInstant(),
            SAO_PAULO);

    @TempDir
    Path tempDir;

    @Test
    void deveUsarAgendaPadraoRelogioESobrescreverArquivoSemanalNoDiretorioDeDados() throws IOException {
        Path dadosTemp = tempDir.resolve("dados-isolados");
        Files.createDirectories(dadosTemp);
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));

        Path arquivoPreExistente = dadosTemp.resolve("semana/2026-W48.md");
        Files.createDirectories(arquivoPreExistente.getParent());
        Files.writeString(arquivoPreExistente, "CONTEUDO OBSOLETO\r\n", StandardCharsets.UTF_8);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository agendaRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()), factory.getValidator());
            AgendaService agendaService = new AgendaService(agendaRepo, RELOGIO_W48);
            PlanoSemanalService service = new PlanoSemanalService(
                    agendaService,
                    new MarkdownSemanalExportador(),
                    new MarkdownPlanoSemanalRepository(),
                    dadosTemp);

            PlanoSemanalGerado primeira = service.gerarEExportar(null, null);
            assertThat(primeira.arquivoSaida()).isEqualTo(dadosTemp.resolve("semana/2026-W48.md"));
            assertThat(primeira.semanaPlanejada().semana().ano()).isEqualTo(2026);
            assertThat(primeira.semanaPlanejada().semana().numeroIso()).isEqualTo(48);

            String conteudoPrimeiro = Files.readString(primeira.arquivoSaida(), StandardCharsets.UTF_8);
            assertThat(conteudoPrimeiro)
                    .startsWith("# Semana 2026-W48 (23/11 a 29/11)")
                    .doesNotContain("CONTEUDO OBSOLETO")
                    .doesNotContain("\r");

            PlanoSemanalGerado segunda = service.gerarEExportar(null, null);
            byte[] bytesPrimeira = Files.readAllBytes(primeira.arquivoSaida());
            byte[] bytesSegunda = Files.readAllBytes(segunda.arquivoSaida());
            assertThat(bytesSegunda).isEqualTo(bytesPrimeira);
        }
    }

    @Test
    void deveUsarAgendaAlternativaEDataExplicitaNaViradaDeAnoIsoMantendoSaidaNoDiretorioDeDados()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-destino");
        Path agendaExterna = Path.of("exemplos/agenda.exemplo.yaml");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository agendaRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()), factory.getValidator());
            AgendaService agendaService = new AgendaService(agendaRepo, RELOGIO_W48);
            PlanoSemanalService service = new PlanoSemanalService(
                    agendaService,
                    new MarkdownSemanalExportador(),
                    new MarkdownPlanoSemanalRepository(),
                    dadosTemp);

            PlanoSemanalGerado gerado = service.gerarEExportar(agendaExterna, LocalDate.of(2025, 12, 31));

            assertThat(gerado.arquivoSaida()).isEqualTo(dadosTemp.resolve("semana/2026-W01.md"));
            assertThat(Files.exists(dadosTemp.resolve("agenda.yaml"))).isFalse();
            String conteudo = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);
            assertThat(conteudo)
                    .startsWith("# Semana 2026-W01 (29/12 a 04/01)")
                    .doesNotContain("\r");
        }
    }

    @Test
    void deveValidarParametrosObrigatoriosNoConstrutor() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(new ObjectMapper(new YAMLFactory()), factory.getValidator()),
                    RELOGIO_W48);
            MarkdownSemanalExportador exportador = new MarkdownSemanalExportador();
            PlanoSemanalRepository repo = new MarkdownPlanoSemanalRepository();

            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(null, exportador, repo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, null, repo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, exportador, null, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, exportador, repo, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, exportador, repo, Path.of("   ")));
            assertThatThrownBy(() -> new PlanoSemanalService(agendaService, exportador, repo, tempDir)
                            .gerarEExportar(Path.of("exemplos/agenda.exemplo.yaml"), tempDir.resolve("backlog.yaml"), null))
                    .isInstanceOf(IllegalStateException.class);

            BacklogRepository backlogRepo = new YamlBacklogRepository(
                    new ObjectMapper(new YAMLFactory()), factory.getValidator());
            PlanejamentoService planejamento = new PlanejamentoService(new PrioridadeAlocadorStrategy());

            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(null, backlogRepo, planejamento, exportador, repo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, null, planejamento, exportador, repo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, backlogRepo, null, exportador, repo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, backlogRepo, planejamento, null, repo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, backlogRepo, planejamento, exportador, null, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlanoSemanalService(agendaService, backlogRepo, planejamento, exportador, repo, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PlanoSemanalService(
                            agendaService, backlogRepo, planejamento, exportador, repo, Path.of("  ")));
        }
    }

    @Test
    void deveDetectarBacklogPadraoQuandoPresenteEPermitirSobrescritaComBacklogExplicito()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-backlog");
        Files.createDirectories(dadosTemp);
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));

        Path backlogPadrao = dadosTemp.resolve("backlog.yaml");
        Files.writeString(backlogPadrao, """
                tarefas:
                  - id: "collections"
                    titulo: "Java Collections"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        Path backlogAlternativo = tempDir.resolve("backlog-alt.yaml");
        Files.writeString(backlogAlternativo, """
                tarefas:
                  - id: "spring"
                    titulo: "Spring Boot"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(yamlMapper, factory.getValidator()), RELOGIO_W48);
            BacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, factory.getValidator());
            PlanejamentoService planejamento = new PlanejamentoService(new PrioridadeAlocadorStrategy());

            PlanoSemanalService service = new PlanoSemanalService(
                    agendaService,
                    backlogRepo,
                    planejamento,
                    new MarkdownSemanalExportador(),
                    new MarkdownPlanoSemanalRepository(),
                    dadosTemp);

            PlanoSemanalGerado comPadrao = service.gerarEExportar(null, null);
            assertThat(comPadrao.resultadoAlocacao()).isNotNull();
            assertThat(comPadrao.resultadoAlocacao().alocados()).hasSize(2);
            assertThat(Files.readString(comPadrao.arquivoSaida(), StandardCharsets.UTF_8))
                    .contains("| 07:00–07:25 | **Bloco profundo** | Java Collections (1/2) |")
                    .contains("- [ ] seg 07:25–07:50 · Java Collections (2/2)");

            PlanoSemanalGerado comExplicito = service.gerarEExportar(null, backlogAlternativo, null);
            assertThat(comExplicito.resultadoAlocacao()).isNotNull();
            assertThat(comExplicito.resultadoAlocacao().alocados()).hasSize(1);
            assertThat(Files.readString(comExplicito.arquivoSaida(), StandardCharsets.UTF_8))
                    .contains("| 07:00–07:25 | **Bloco profundo** | Spring Boot (1/1) |")
                    .doesNotContain("Java Collections");

            Path backlogInexistente = tempDir.resolve("nao-existe.yaml");
            assertThatThrownBy(() -> service.gerarEExportar(null, backlogInexistente, null))
                    .isInstanceOf(DadosInvalidosException.class);
        }
    }

    @Test
    void devePropagarNotaPareiAquiDeSemanasAnterioresParaOSomentePrimeiroPedacoDoChecklist()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-retomada");
        Files.createDirectories(dadosTemp);
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));
        Files.writeString(dadosTemp.resolve("backlog.yaml"), """
                tarefas:
                  - id: "collections"
                    titulo: "Java Collections"
                    duracao_total_min: 75
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();
        feedbackRepo.salvar(
                dadosTemp.resolve("feedback/2026-W47.md"),
                new FeedbackSemanal(
                        2026,
                        47,
                        List.of(new ItemFeedback(
                                DayOfWeek.WEDNESDAY,
                                LocalTime.of(19, 0),
                                LocalTime.of(19, 25),
                                "Java Collections (2/6)",
                                StatusBloco.INTERROMPIDO,
                                "parei no card 12")),
                        List.of()));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(yamlMapper, factory.getValidator()), RELOGIO_W48);
            BacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, factory.getValidator());
            PlanejamentoService planejamento = new PlanejamentoService(new PrioridadeAlocadorStrategy());
            FeedbackService feedbackService = new FeedbackService(
                    agendaService, backlogRepo, planejamento, feedbackRepo, dadosTemp);
            MarkdownSemanalExportador exportador = new MarkdownSemanalExportador();
            PlanoSemanalRepository planoRepo = new MarkdownPlanoSemanalRepository();

            assertThatNullPointerException().isThrownBy(() -> new PlanoSemanalService(
                    agendaService, backlogRepo, planejamento, null, exportador, planoRepo, dadosTemp));

            PlanoSemanalService service = new PlanoSemanalService(
                    agendaService,
                    backlogRepo,
                    planejamento,
                    feedbackService,
                    exportador,
                    planoRepo,
                    dadosTemp);

            PlanoSemanalGerado gerado = service.gerarEExportar(null, null, null);
            String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);
            assertThat(markdown).contains("""
                    ## Checklist de estudos
                    - [ ] seg 07:00–07:25 · Java Collections (1/3) — retomar: parei no card 12
                    - [ ] seg 07:25–07:50 · Java Collections (2/3)
                    - [ ] seg 07:50–08:15 · Java Collections (3/3)
                    """);
            assertThat(markdown.split("— retomar:", -1)).hasSize(2);
        }
    }
}
