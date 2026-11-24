package dev.cadencia.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cadencia.agenda.AgendaService;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.feedback.FeedbackRepository;
import dev.cadencia.feedback.FeedbackService;
import dev.cadencia.feedback.MarkdownFeedbackRepository;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class FeedbackCommandIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static Path diretorioDadosTemporario;

    @Autowired
    private PicocliCommandRunner runner;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @DynamicPropertySource
    static void configurarDiretorioDadosTemporario(DynamicPropertyRegistry registry) throws IOException {
        diretorioDadosTemporario = Files.createTempDirectory("cadencia-feedback-cli-");
        registry.add("cadencia.diretorio-dados", () -> diretorioDadosTemporario.toString());
    }

    @AfterAll
    static void limparDiretorioTemporario() throws IOException {
        apagarArvore(diretorioDadosTemporario);
    }

    @BeforeEach
    void limparFeedbacksEntreTestes() throws IOException {
        apagarArvore(diretorioDadosTemporario.resolve("feedback"));
    }

    private static void apagarArvore(Path raiz) throws IOException {
        if (raiz == null || !Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(caminho);
            }
        }
    }

    @Test
    void deveExibirAjudaDoSubcomandoFeedbackComExitCodeZeroSemEfeitosColaterais() throws IOException {
        Path dadosHelp = Files.createTempDirectory(diretorioDadosTemporario, "help-");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int codigo = runner.executar(new PrintWriter(out), new PrintWriter(err), "feedback", "--help");

        assertThat(codigo).isZero();
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString()).contains("feedback", "--modelo", "--sobrescrever", "--backlog");
        assertThat(Files.exists(dadosHelp.resolve("feedback"))).isFalse();
    }

    @Test
    void deveGerarModeloUsandoBacklogAutomaticoProtegerContraSobrescritaERecriarComSobrescrever()
            throws IOException {
        Files.copy(
                Path.of("exemplos/agenda.exemplo.yaml"),
                diretorioDadosTemporario.resolve("agenda.yaml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(
                Path.of("exemplos/backlog.exemplo.yaml"),
                diretorioDadosTemporario.resolve("backlog.yaml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path destino = diretorioDadosTemporario.resolve("feedback/2026-W48.md");
        Files.deleteIfExists(destino);

        StringWriter out1 = new StringWriter();
        StringWriter err1 = new StringWriter();
        int codigo1 = runner.executar(new PrintWriter(out1), new PrintWriter(err1), "feedback", "--modelo");

        assertThat(codigo1).isZero();
        assertThat(err1.toString()).isEmpty();
        assertThat(out1.toString()).contains("2026-W48", destino.toString(), "30 item(ns)");

        String markdownOriginal = Files.readString(destino, StandardCharsets.UTF_8);
        assertThat(markdownOriginal)
                .doesNotContain("\r")
                .startsWith("# Feedback 2026-W48\n")
                .contains("- [ ] seg 07:00–07:25 · Java Core: Streams, Lambdas e Records (1/4) | NAO_ACONTECEU\n")
                .contains("- [ ] qua 15:00–15:20 · Flashcards e Revisão Leve de Conceitos Java (3/3) | NAO_ACONTECEU\n");
        FeedbackSemanal carregado = feedbackRepository.carregar(destino);
        assertThat(carregado.itens()).hasSize(30);

        String anotacaoUsuario = "# Feedback 2026-W48\n- [x] seg 07:00 Java Core (1/4) | CONCLUIDO\n";
        Files.writeString(destino, anotacaoUsuario, StandardCharsets.UTF_8);

        StringWriter outSemSobrescrever = new StringWriter();
        StringWriter errSemSobrescrever = new StringWriter();
        int codSemSobrescrever = runner.executar(
                new PrintWriter(outSemSobrescrever),
                new PrintWriter(errSemSobrescrever),
                "feedback", "--modelo");

        assertThat(codSemSobrescrever).isEqualTo(2);
        assertThat(errSemSobrescrever.toString())
                .contains(destino.toString(), "--sobrescrever")
                .doesNotContain("at dev.cadencia.");
        assertThat(Files.readString(destino, StandardCharsets.UTF_8)).isEqualTo(anotacaoUsuario);

        StringWriter outSobrescrever = new StringWriter();
        StringWriter errSobrescrever = new StringWriter();
        int codSobrescrever = runner.executar(
                new PrintWriter(outSobrescrever),
                new PrintWriter(errSobrescrever),
                "feedback", "--modelo", "--sobrescrever");

        assertThat(codSobrescrever).isZero();
        assertThat(errSobrescrever.toString()).isEmpty();
        assertThat(Files.readString(destino, StandardCharsets.UTF_8)).isEqualTo(markdownOriginal);
    }

    @Test
    void deveGerarModeloSemBacklogComDataExplicitaEExibirAvisoQuandoHouverNaoAlocados()
            throws IOException {
        Path agendaIsolada = Path.of("exemplos/agenda.exemplo.yaml");
        Path backlogComSobra = Files.createTempFile(diretorioDadosTemporario, "backlog-sobra-", ".yaml");
        Files.writeString(backlogComSobra, """
                tarefas:
                  - id: "curta"
                    titulo: "Revisão Rápida"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: "gigante"
                    titulo: "Tarefa Gigante"
                    duracao_total_min: 400
                    tamanho_pedaco_min: 200
                    tipos_aceitos: [VIGILANCIA]
                    prioridade: 2
                """, StandardCharsets.UTF_8);

        Path saidaW50 = diretorioDadosTemporario.resolve("feedback/2026-W50.md");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int codigoW50 = runner.executar(
                new PrintWriter(out),
                new PrintWriter(err),
                "feedback",
                "--modelo",
                "--agenda", agendaIsolada.toString(),
                "--backlog", backlogComSobra.toString(),
                "--data", "2026-12-09",
                "--sobrescrever");

        assertThat(codigoW50).isZero();
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString())
                .contains("2026-W50", "1 item(ns)")
                .contains("não couberam nesta semana");
        assertThat(Files.readString(saidaW50, StandardCharsets.UTF_8))
                .contains("- [ ] seg 07:00–07:25 · Revisão Rápida (1/1) | NAO_ACONTECEU");

        // Gera semana futura sem usar backlog (apagando backlog.yaml temporário primeiro)
        Files.deleteIfExists(diretorioDadosTemporario.resolve("backlog.yaml"));
        Path saidaW01 = diretorioDadosTemporario.resolve("feedback/2026-W01.md");
        StringWriter outSemBacklog = new StringWriter();
        StringWriter errSemBacklog = new StringWriter();
        int codigoSemBacklog = runner.executar(
                new PrintWriter(outSemBacklog),
                new PrintWriter(errSemBacklog),
                "feedback",
                "--modelo",
                "--agenda", agendaIsolada.toString(),
                "--data", "2025-12-31");

        assertThat(codigoSemBacklog).isZero();
        assertThat(errSemBacklog.toString()).isEmpty();
        assertThat(outSemBacklog.toString()).contains("2026-W01", "24 item(ns)");
        assertThat(Files.readString(saidaW01, StandardCharsets.UTF_8))
                .startsWith("# Feedback 2026-W01\n")
                .contains("- [ ] seg 07:00–08:30 · Bloco profundo (1h30) | NAO_ACONTECEU");
    }

    @Test
    void deveRetornarCodigo2ParaEntradasInvalidasECodigo1ParaErroInesperado() throws IOException {
        StringWriter errSemModelo = new StringWriter();
        assertThat(runner.executar(new PrintWriter(new StringWriter()), new PrintWriter(errSemModelo), "feedback"))
                .isEqualTo(2);
        assertThat(errSemModelo.toString()).contains("--modelo");

        StringWriter errModeloFalso = new StringWriter();
        assertThat(runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errModeloFalso),
                "feedback", "--modelo=false"))
                .isEqualTo(2);
        assertThat(errModeloFalso.toString()).contains("--modelo");

        StringWriter errData = new StringWriter();
        assertThat(runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errData),
                "--verbose", "feedback", "--modelo", "--data", "invalida"))
                .isEqualTo(2);
        assertThat(errData.toString()).contains("--data").doesNotContain("at dev.cadencia.");

        StringWriter errAgendaBranco = new StringWriter();
        assertThat(runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errAgendaBranco),
                "feedback", "--modelo", "--agenda", "   "))
                .isEqualTo(2);
        assertThat(errAgendaBranco.toString()).contains("--agenda").doesNotContain("at dev.cadencia.");

        StringWriter errBacklogBranco = new StringWriter();
        assertThat(runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errBacklogBranco),
                "feedback", "--modelo", "--backlog", " "))
                .isEqualTo(2);
        assertThat(errBacklogBranco.toString()).contains("--backlog").doesNotContain("at dev.cadencia.");

        StringWriter errBacklogInex = new StringWriter();
        assertThat(runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errBacklogInex),
                "feedback", "--modelo",
                "--agenda", "exemplos/agenda.exemplo.yaml",
                "--backlog", diretorioDadosTemporario.resolve("nao-existe-backlog.yaml").toString()))
                .isEqualTo(2);
        assertThat(errBacklogInex.toString()).contains("nao-existe-backlog.yaml");

        // Simula falha inesperada em runtime para garantir exit code 1 e --verbose
        FeedbackRepository repoQueFalha = new MarkdownFeedbackRepository() {
            @Override
            public Path salvar(Path destino, FeedbackSemanal feedback) {
                throw new IllegalStateException("Falha simulada ao salvar feedback");
            }
        };
        FeedbackService servicoQueFalha = new FeedbackService(
                applicationContext.getBean(AgendaService.class),
                repoQueFalha,
                diretorioDadosTemporario);

        GenericApplicationContext contextoComFalha = new GenericApplicationContext();
        contextoComFalha.registerBean(
                CadenciaCommand.class,
                CadenciaCommand::new,
                bd -> bd.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE));
        contextoComFalha.registerBean(
                FeedbackCommand.class,
                () -> new FeedbackCommand(servicoQueFalha),
                bd -> bd.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE));
        contextoComFalha.registerBean(
                SemanaCommand.class, () -> applicationContext.getBean(SemanaCommand.class));
        contextoComFalha.refresh();

        PicocliCommandRunner runnerComFalha = new PicocliCommandRunner(new PicocliSpringFactory(contextoComFalha));

        StringWriter errSemVerbose = new StringWriter();
        assertThat(runnerComFalha.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errSemVerbose),
                "feedback", "--modelo", "--agenda", "exemplos/agenda.exemplo.yaml", "--sobrescrever"))
                .isEqualTo(1);
        assertThat(errSemVerbose.toString())
                .contains("Erro inesperado: Falha simulada ao salvar feedback", "--verbose")
                .doesNotContain("java.lang.IllegalStateException");

        StringWriter errComVerbose = new StringWriter();
        assertThat(runnerComFalha.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errComVerbose),
                "feedback", "--modelo", "--verbose",
                "--agenda", "exemplos/agenda.exemplo.yaml", "--sobrescrever"))
                .isEqualTo(1);
        assertThat(errComVerbose.toString())
                .contains("java.lang.IllegalStateException: Falha simulada ao salvar feedback");
    }

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixoTeste() {
            return Clock.fixed(
                    LocalDate.of(2026, 11, 23).atTime(9, 0).atZone(SAO_PAULO).toInstant(),
                    SAO_PAULO);
        }
    }
}
