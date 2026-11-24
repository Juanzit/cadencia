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
import dev.cadencia.dominio.StatusBloco;
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
}
