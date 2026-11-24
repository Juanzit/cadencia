package dev.cadencia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.StatusBloco;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.YamlBacklogRepository;
import dev.cadencia.planejamento.strategies.ConfiabilidadeAlocadorStrategy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FeedbackServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO_W48 = Clock.fixed(
            LocalDate.of(2026, 11, 23).atTime(9, 0).atZone(SAO_PAULO).toInstant(),
            SAO_PAULO);

    @TempDir
    Path tempDir;

    @Test
    void deveGerarModeloSemBacklogComBlocosLivresEProtegerContraSobrescrita() throws IOException {
        Path dadosTemp = tempDir.resolve("dados-sem-backlog");
        Files.createDirectories(dadosTemp);
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository agendaRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()), factory.getValidator());
            AgendaService agendaService = new AgendaService(agendaRepo, RELOGIO_W48);
            FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();
            FeedbackService service = new FeedbackService(agendaService, feedbackRepo, dadosTemp);

            ModeloFeedbackGerado gerado = service.gerarModelo(null, null, false);
            Path destinoEsperado = dadosTemp.resolve("feedback/2026-W48.md");
            assertThat(gerado.arquivoSaida()).isEqualTo(destinoEsperado);
            assertThat(gerado.resultadoAlocacao()).isNull();
            assertThat(gerado.feedbackSemanal().itens()).hasSize(24);
            assertThat(gerado.feedbackSemanal().itens())
                    .allSatisfy(item -> assertThat(item.status()).isEqualTo(StatusBloco.NAO_ACONTECEU));

            String conteudo = Files.readString(destinoEsperado, StandardCharsets.UTF_8);
            assertThat(conteudo)
                    .doesNotContain("\r")
                    .startsWith("# Feedback 2026-W48\n\n## Blocos e estudos\n")
                    .contains("- [ ] seg 07:00–08:30 · Bloco profundo (1h30) | NAO_ACONTECEU\n")
                    .contains("- [ ] seg 11:30–12:00 · Bloco interrompível (0h30) | NAO_ACONTECEU\n")
                    .contains("- [ ] qua 15:00–17:00 · Janela de plantão compatível apenas com tarefas leves | NAO_ACONTECEU\n")
                    .contains("- [ ] sex 10:00–11:30 · Reserva manual para estudo prático | NAO_ACONTECEU\n")
                    .endsWith("## Notas livres\n- Nenhuma nota registrada.\n");

            assertThat(feedbackRepo.carregar(destinoEsperado)).isEqualTo(gerado.feedbackSemanal());

            String editadoPeloUsuario = "# Feedback 2026-W48\n- [x] seg 07:00 Java | CONCLUIDO\n";
            Files.writeString(destinoEsperado, editadoPeloUsuario, StandardCharsets.UTF_8);

            assertThatThrownBy(() -> service.gerarModelo(null, null, false))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining(destinoEsperado.toString())
                    .hasMessageContaining("--sobrescrever");
            assertThat(Files.readString(destinoEsperado, StandardCharsets.UTF_8))
                    .isEqualTo(editadoPeloUsuario);

            ModeloFeedbackGerado sobrescrito = service.gerarModelo(null, null, true);
            assertThat(Files.readString(sobrescrito.arquivoSaida(), StandardCharsets.UTF_8))
                    .isEqualTo(conteudo);
        }
    }

    @Test
    void deveDetectarBacklogPadraoAutomaticamenteEPermitirSobrescritaPorBacklogExplicito()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-com-backlog");
        Files.createDirectories(dadosTemp);
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));
        Files.copy(Path.of("exemplos/backlog.exemplo.yaml"), dadosTemp.resolve("backlog.yaml"));

        Path backlogVazio = tempDir.resolve("backlog-vazio.yaml");
        Files.writeString(backlogVazio, "tarefas: []\n", StandardCharsets.UTF_8);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(yamlMapper, factory.getValidator()), RELOGIO_W48);
            BacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, factory.getValidator());
            PlanejamentoService planejamento = new PlanejamentoService(new PrioridadeAlocadorStrategy());
            FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();

            FeedbackService service = new FeedbackService(
                    agendaService, backlogRepo, planejamento, feedbackRepo, dadosTemp);

            ModeloFeedbackGerado comPadrao = service.gerarModelo(null, null, null, false);
            assertThat(comPadrao.resultadoAlocacao()).isNotNull();
            assertThat(comPadrao.resultadoAlocacao().alocados()).hasSize(30);
            assertThat(comPadrao.feedbackSemanal().itens()).hasSize(30);
            assertThat(comPadrao.feedbackSemanal().itens().get(0))
                    .satisfies(primeiro -> {
                        assertThat(primeiro.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
                        assertThat(primeiro.inicio()).isEqualTo(LocalTime.of(7, 0));
                        assertThat(primeiro.fim()).isEqualTo(LocalTime.of(7, 25));
                        assertThat(primeiro.titulo()).isEqualTo("Java Core: Streams, Lambdas e Records (1/4)");
                        assertThat(primeiro.status()).isEqualTo(StatusBloco.NAO_ACONTECEU);
                    });

            FeedbackSemanal lido = feedbackRepo.carregar(comPadrao.arquivoSaida());
            assertThat(lido).isEqualTo(comPadrao.feedbackSemanal());

            ModeloFeedbackGerado viradaComVazio = service.gerarModelo(
                    Path.of("exemplos/agenda.exemplo.yaml"),
                    backlogVazio,
                    LocalDate.of(2025, 12, 31),
                    false);
            assertThat(viradaComVazio.arquivoSaida())
                    .isEqualTo(dadosTemp.resolve("feedback/2026-W01.md"));
            assertThat(viradaComVazio.resultadoAlocacao()).isNotNull();
            assertThat(viradaComVazio.resultadoAlocacao().alocados()).isEmpty();
            assertThat(viradaComVazio.feedbackSemanal().itens()).hasSize(24);
        }
    }

    @Test
    void deveValidarConstrutoresEMensagemDeErroQuandoBacklogForInformadoSemSuporte() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(yamlMapper, factory.getValidator()), RELOGIO_W48);
            BacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, factory.getValidator());
            PlanejamentoService planejamento = new PlanejamentoService(new PrioridadeAlocadorStrategy());
            FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();

            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(null, feedbackRepo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(agendaService, null, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(agendaService, feedbackRepo, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FeedbackService(agendaService, feedbackRepo, Path.of("   ")));

            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(null, backlogRepo, planejamento, feedbackRepo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(agendaService, null, planejamento, feedbackRepo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(agendaService, backlogRepo, null, feedbackRepo, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(agendaService, backlogRepo, planejamento, null, tempDir));
            assertThatNullPointerException()
                    .isThrownBy(() -> new FeedbackService(agendaService, backlogRepo, planejamento, feedbackRepo, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FeedbackService(
                            agendaService, backlogRepo, planejamento, feedbackRepo, Path.of(" ")));

            FeedbackService semBacklogSupport = new FeedbackService(agendaService, feedbackRepo, tempDir);
            assertThatThrownBy(() -> semBacklogSupport.gerarModelo(
                            Path.of("exemplos/agenda.exemplo.yaml"),
                            Path.of("exemplos/backlog.exemplo.yaml"),
                            null,
                            false))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void deveResolverNotasDeRetomadaDeterministicamenteSemMutarHistoricoEIgnorandoSemanasFuturas()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-retomada");
        Files.createDirectories(dadosTemp);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(new ObjectMapper(new YAMLFactory()), factory.getValidator()),
                    RELOGIO_W48);
            FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();
            FeedbackService service = new FeedbackService(agendaService, feedbackRepo, dadosTemp);

            Semana semanaW48 = new Semana(2026, 48, List.of(), List.of());
            Semana semanaW49 = new Semana(2026, 49, List.of(), List.of());
            Semana semanaW01Ano2027 = new Semana(2027, 1, List.of(), List.of());

            // 1. Mesmo dia, horários diferentes, fim == null, empate e ordem reversa na lista de entrada
            FeedbackSemanal w47ForaDeOrdem = new FeedbackSemanal(
                    2026,
                    47,
                    List.of(
                            new ItemFeedback(
                                    DayOfWeek.MONDAY,
                                    LocalTime.of(19, 0),
                                    LocalTime.of(19, 25),
                                    "Java Collections (2/6)",
                                    StatusBloco.INTERROMPIDO,
                                    "parei no card 12"),
                            new ItemFeedback(
                                    DayOfWeek.MONDAY,
                                    LocalTime.of(7, 0),
                                    null,
                                    "Java Collections",
                                    StatusBloco.INTERROMPIDO,
                                    "parei no card 3"),
                            new ItemFeedback(
                                    DayOfWeek.TUESDAY,
                                    LocalTime.of(8, 0),
                                    null,
                                    "Empate",
                                    StatusBloco.INTERROMPIDO,
                                    "primeira nota empatada"),
                            new ItemFeedback(
                                    DayOfWeek.TUESDAY,
                                    LocalTime.of(8, 0),
                                    null,
                                    "Empate",
                                    StatusBloco.INTERROMPIDO,
                                    "segunda nota empatada"),
                            new ItemFeedback(
                                    DayOfWeek.WEDNESDAY,
                                    LocalTime.of(10, 0),
                                    LocalTime.of(10, 25),
                                    "Spring Boot (1/5)",
                                    StatusBloco.INTERROMPIDO,
                                    "parei no controller")),
                    List.of());

            // 2. Semana posterior concluindo Spring Boot (mesmo com nota residual preenchida!)
            //    e mantendo Java Collections sem nova nota via NAO_ACONTECEU / INTERROMPIDO sem nota
            FeedbackSemanal w48 = new FeedbackSemanal(
                    2026,
                    48,
                    List.of(
                            new ItemFeedback(
                                    DayOfWeek.MONDAY,
                                    LocalTime.of(7, 0),
                                    LocalTime.of(7, 25),
                                    "Spring Boot (1/5)",
                                    StatusBloco.CONCLUIDO,
                                    "nota residual que deve ser removida pela conclusão"),
                            new ItemFeedback(
                                    DayOfWeek.TUESDAY,
                                    LocalTime.of(7, 0),
                                    LocalTime.of(7, 25),
                                    "Java Collections",
                                    StatusBloco.NAO_ACONTECEU,
                                    null),
                            new ItemFeedback(
                                    DayOfWeek.WEDNESDAY,
                                    LocalTime.of(7, 0),
                                    LocalTime.of(7, 25),
                                    "Empate",
                                    StatusBloco.INTERROMPIDO,
                                    null),
                            new ItemFeedback(
                                    DayOfWeek.THURSDAY,
                                    LocalTime.of(9, 0),
                                    LocalTime.of(9, 25),
                                    "JPA (1/4)",
                                    StatusBloco.INTERROMPIDO,
                                    "parei no mapeamento N:N")),
                    List.of());

            FeedbackSemanal futuroW49 = new FeedbackSemanal(
                    2026,
                    49,
                    List.of(new ItemFeedback(
                            DayOfWeek.FRIDAY,
                            LocalTime.of(8, 0),
                            LocalTime.of(8, 25),
                            "SQL (1/3)",
                            StatusBloco.INTERROMPIDO,
                            "nota futura nao pode vazar")),
                    List.of());

            List<FeedbackSemanal> historicoOriginal = new ArrayList<>(List.of(futuroW49, w48, w47ForaDeOrdem));
            List<FeedbackSemanal> snapshotOriginal = List.copyOf(historicoOriginal);

            // Ao regenerar 2026-W48: somente 2026-W47 influencia (W48 e W49 futuras são ignoradas)
            Map<String, String> notasParaW48 = service.resolverNotasRetomada(semanaW48, historicoOriginal);
            assertThat(notasParaW48)
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "Java Collections", "parei no card 12",
                            "Empate", "segunda nota empatada",
                            "Spring Boot", "parei no controller"));
            assertThat(historicoOriginal).isEqualTo(snapshotOriginal);
            assertThatThrownBy(() -> notasParaW48.put("Outra", "Valor"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> notasParaW48.remove("Java Collections"))
                    .isInstanceOf(UnsupportedOperationException.class);

            // Ao gerar 2026-W49: Spring Boot foi concluído em W48 (nota removida); Java Collections e Empate preservados;
            // W49 futuro ainda ignorado
            Map<String, String> notasParaW49 = service.resolverNotasRetomada(semanaW49, historicoOriginal);
            assertThat(notasParaW49)
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "Java Collections", "parei no card 12",
                            "Empate", "segunda nota empatada",
                            "JPA", "parei no mapeamento N:N"))
                    .doesNotContainKey("Spring Boot")
                    .doesNotContainKey("SQL");

            // Virada de ano: 2027-W01 enxerga também 2026-W49
            Map<String, String> notasPara2027W01 =
                    service.resolverNotasRetomada(semanaW01Ano2027, historicoOriginal);
            assertThat(notasPara2027W01).containsEntry("SQL", "nota futura nao pode vazar");

            // Integração via repositório em disco
            feedbackRepo.salvar(dadosTemp.resolve("feedback/2026-W47.md"), w47ForaDeOrdem);
            feedbackRepo.salvar(dadosTemp.resolve("feedback/2026-W48.md"), w48);
            feedbackRepo.salvar(dadosTemp.resolve("feedback/2026-W49.md"), futuroW49);
            assertThat(service.resolverNotasRetomada(semanaW48)).isEqualTo(notasParaW48);
            assertThat(service.resolverNotasRetomada(semanaW49)).isEqualTo(notasParaW49);
        }
    }

    @Test
    void deveGerarModeloConsiderandoConfiabilidadeQuandoConfiabilidadeServiceForInjetado()
            throws IOException {
        Path dadosTemp = tempDir.resolve("dados-modelo-confiabilidade");
        Files.createDirectories(dadosTemp);
        Files.copy(Path.of("exemplos/agenda.exemplo.yaml"), dadosTemp.resolve("agenda.yaml"));
        Files.writeString(dadosTemp.resolve("backlog.yaml"), """
                tarefas:
                  - id: "core"
                    titulo: "Java Core"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: "junit"
                    titulo: "Testes JUnit"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO, INTERROMPIVEL]
                    prioridade: 2
                """, StandardCharsets.UTF_8);

        FeedbackRepository feedbackRepo = new MarkdownFeedbackRepository();
        for (int iso = 45; iso <= 47; iso++) {
            feedbackRepo.salvar(
                    dadosTemp.resolve("feedback/2026-W" + iso + ".md"),
                    new FeedbackSemanal(
                            2026,
                            iso,
                            List.of(new ItemFeedback(
                                    DayOfWeek.MONDAY,
                                    LocalTime.of(7, 0),
                                    LocalTime.of(7, 25),
                                    "Java",
                                    StatusBloco.NAO_ACONTECEU,
                                    null)),
                            List.of()));
        }

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            AgendaService agendaService = new AgendaService(
                    new YamlAgendaRepository(yamlMapper, factory.getValidator()), RELOGIO_W48);
            BacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, factory.getValidator());
            PlanejamentoService planejamento = new PlanejamentoService(new ConfiabilidadeAlocadorStrategy());
            ConfiabilidadeService confiabilidadeService = new ConfiabilidadeService(feedbackRepo, dadosTemp);

            assertThatNullPointerException().isThrownBy(() -> new FeedbackService(
                    agendaService, backlogRepo, planejamento, feedbackRepo, null, dadosTemp));

            FeedbackService service = new FeedbackService(
                    agendaService,
                    backlogRepo,
                    planejamento,
                    feedbackRepo,
                    confiabilidadeService,
                    dadosTemp);

            ModeloFeedbackGerado gerado = service.gerarModelo(null, null, null, false);
            assertThat(gerado.feedbackSemanal().itens()).hasSize(2);
            assertThat(gerado.feedbackSemanal().itens().get(0).titulo()).isEqualTo("Testes JUnit (1/1)");
            assertThat(gerado.feedbackSemanal().itens().get(0).inicio()).isEqualTo(LocalTime.of(7, 0));
            assertThat(gerado.feedbackSemanal().itens().get(1).titulo()).isEqualTo("Java Core (1/1)");
            assertThat(gerado.feedbackSemanal().itens().get(1).inicio()).isEqualTo(LocalTime.of(13, 10));
        }
    }
}
