package dev.cadencia.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.exportacao.strategies.MarkdownSemanalExportador;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        }
    }
}
