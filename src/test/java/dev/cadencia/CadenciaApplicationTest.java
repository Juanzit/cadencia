package dev.cadencia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cadencia.cli.CadenciaCommand;
import dev.cadencia.cli.PicocliCommandRunner;
import dev.cadencia.cli.PicocliSpringFactory;
import dev.cadencia.cli.SemanaCommand;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.StatusBloco;
import dev.cadencia.exportacao.PlanoSemanalService;
import dev.cadencia.feedback.ConfiabilidadeService;
import dev.cadencia.feedback.FeedbackRepository;
import dev.cadencia.feedback.MarkdownFeedbackRepository;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.strategies.AlocadorStrategy;
import dev.cadencia.planejamento.strategies.ConfiabilidadeAlocadorStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class CadenciaApplicationTest {

    private static Path diretorioDadosTemporario;

    @Value("${cadencia.fuso}")
    private String fuso;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ExitCodeGenerator exitCodeGenerator;

    @DynamicPropertySource
    static void configurarDiretorioDadosTemporario(DynamicPropertyRegistry registry) throws IOException {
        diretorioDadosTemporario = Files.createTempDirectory("cadencia-app-test-");
        registry.add("cadencia.diretorio-dados", () -> diretorioDadosTemporario.toString());
    }

    @AfterAll
    static void limparDiretorioTemporario() throws IOException {
        if (diretorioDadosTemporario == null || !Files.exists(diretorioDadosTemporario)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(diretorioDadosTemporario)) {
            for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(caminho);
            }
        }
    }

    @Test
    void contextoSobeSemArgumentosExibindoAjudaSemEfeitosColaterais(CapturedOutput output) throws IOException {
        assertThat(fuso).isEqualTo("America/Sao_Paulo");
        assertThat(exitCodeGenerator.getExitCode()).isZero();
        assertThat(applicationContext.getBean(PicocliSpringFactory.class)).isNotNull();
        assertThat(applicationContext.getBean(PicocliCommandRunner.class)).isNotNull();
        assertThat(applicationContext.getBean(CadenciaCommand.class)).isNotNull();
        assertThat(applicationContext.getBean(SemanaCommand.class)).isNotNull();
        assertThat(applicationContext.getBean(BacklogRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(AlocadorStrategy.class))
                .isInstanceOf(ConfiabilidadeAlocadorStrategy.class);
        assertThat(applicationContext.getBean(ConfiabilidadeService.class)).isNotNull();
        assertThat(applicationContext.getBean(PlanejamentoService.class)).isNotNull();
        assertThat(applicationContext.getBean(PlanoSemanalService.class)).isNotNull();

        try (Stream<Path> arquivosCriados = Files.walk(diretorioDadosTemporario)) {
            assertThat(arquivosCriados.filter(path -> !path.equals(diretorioDadosTemporario))).isEmpty();
        }
        assertThat(output.getOut()).contains("cadencia", "semana", "--verbose");
    }

    @Test
    void executarPropagaExitCodeViaSpringApplicationExitSemEncerrarJvmDosTestes() throws IOException {
        Path dadosExec = Files.createTempDirectory(diretorioDadosTemporario, "spring-exit-");
        Path alvoRealW50 = Path.of("dados/semana/2026-W50.md");
        boolean alvoRealExistia = Files.exists(alvoRealW50);
        Instant modificacaoAntes = alvoRealExistia ? Files.getLastModifiedTime(alvoRealW50).toInstant() : null;

        try (ConfigurableApplicationContext contextoSucesso = new SpringApplicationBuilder(CadenciaApplication.class)
                .initializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testDados", Map.of("cadencia.diretorio-dados", dadosExec.toString()))))
                .run("semana", "--agenda", "exemplos/agenda.exemplo.yaml", "--data", "2026-12-09")) {
            int codigoSucesso = SpringApplication.exit(contextoSucesso);
            assertThat(codigoSucesso).isZero();
        }
        assertThat(Files.exists(dadosExec.resolve("semana/2026-W50.md"))).isTrue();
        assertThat(Files.exists(alvoRealW50)).isEqualTo(alvoRealExistia);
        if (modificacaoAntes != null) {
            assertThat(Files.getLastModifiedTime(alvoRealW50).toInstant()).isEqualTo(modificacaoAntes);
        }

        System.setProperty("cadencia.diretorio-dados", dadosExec.toString());
        try {
            assertThat(CadenciaApplication.executar("--help")).isZero();
            assertThat(CadenciaApplication.executar("semana", "--data", "invalida")).isEqualTo(2);
        } finally {
            System.clearProperty("cadencia.diretorio-dados");
        }
    }

    @Test
    void deveAplicarPropriedadeCadenciaPedacoPadraoMinQuandoOmitidaNoBacklogYaml() throws IOException {
        Path dadosCustom = Files.createTempDirectory(diretorioDadosTemporario, "pedaco-custom-");
        Path backlog = dadosCustom.resolve("backlog.yaml");
        Files.writeString(backlog, """
                tarefas:
                  - id: "java-streams"
                    titulo: "Java Streams"
                    duracao_total_min: 70
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(CadenciaApplication.class)
                .initializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("customPedaco", Map.of(
                                "cadencia.diretorio-dados", dadosCustom.toString(),
                                "cadencia.pedaco-padrao-min", "35"))))
                .run("semana", "--agenda", "exemplos/agenda.exemplo.yaml", "--data", "2026-11-23")) {
            assertThat(SpringApplication.exit(contexto)).isZero();
        }

        String markdown = Files.readString(dadosCustom.resolve("semana/2026-W48.md"), StandardCharsets.UTF_8);
        assertThat(markdown).contains(
                "- Pedaços alocados: 2 (1h10)",
                "| 07:00–07:35 | **Bloco profundo** | Java Streams (1/2) |",
                "| 07:35–08:10 | **Bloco profundo** | Java Streams (2/2) |");
    }

    @Test
    void deveAplicarPropriedadesCustomizadasDeConfiabilidadeNaConfiguracaoSpring() throws IOException {
        Path dadosConfiabilidade = Files.createTempDirectory(diretorioDadosTemporario, "conf-custom-");
        Files.writeString(dadosConfiabilidade.resolve("backlog.yaml"), """
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

        FeedbackRepository repo = new MarkdownFeedbackRepository();
        // W45 foi ruim, W46 foi 0.50 (1.0 e 0.0 -> média 0.50 < limite customizado 0.75) e W47 boa?
        // Com janela-semanas=2 (W46, W47) e minimo-amostras=2, limite=0.75:
        // seg MANHA em W46 (0.0) e W47 (1.0) => média 0.50 (< 0.75, 2 amostras >= 2) => restringida!
        repo.salvar(dadosConfiabilidade.resolve("feedback/2026-W45.md"), new FeedbackSemanal(
                2026, 45,
                List.of(new ItemFeedback(DayOfWeek.MONDAY, LocalTime.of(8, 0), null, "Estudo", StatusBloco.CONCLUIDO, null)),
                List.of()));
        repo.salvar(dadosConfiabilidade.resolve("feedback/2026-W46.md"), new FeedbackSemanal(
                2026, 46,
                List.of(new ItemFeedback(DayOfWeek.MONDAY, LocalTime.of(8, 0), null, "Estudo", StatusBloco.NAO_ACONTECEU, null)),
                List.of()));
        repo.salvar(dadosConfiabilidade.resolve("feedback/2026-W47.md"), new FeedbackSemanal(
                2026, 47,
                List.of(new ItemFeedback(DayOfWeek.MONDAY, LocalTime.of(8, 0), null, "Estudo", StatusBloco.CONCLUIDO, null)),
                List.of()));

        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(CadenciaApplication.class)
                .initializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("customConfiabilidade", Map.of(
                                "cadencia.diretorio-dados", dadosConfiabilidade.toString(),
                                "cadencia.confiabilidade.janela-semanas", "2",
                                "cadencia.confiabilidade.minimo-amostras", "2",
                                "cadencia.confiabilidade.limite-confiabilidade", "0.75"))))
                .run("semana", "--agenda", "exemplos/agenda.exemplo.yaml", "--data", "2026-11-23")) {
            ConfiabilidadeService cs = contexto.getBean(ConfiabilidadeService.class);
            assertThat(cs.janelaSemanas()).isEqualTo(2);
            assertThat(cs.minimoAmostras()).isEqualTo(2);
            assertThat(cs.limiteConfiabilidade()).isEqualTo(0.75);
            assertThat(SpringApplication.exit(contexto)).isZero();
        }

        String markdown = Files.readString(
                dadosConfiabilidade.resolve("semana/2026-W48.md"), StandardCharsets.UTF_8);
        assertThat(markdown).contains(
                "| 07:00–07:25 | **Bloco profundo** | Testes JUnit (1/1) |",
                "| 13:10–13:35 | **Bloco profundo** | Java Core (1/1) |",
                "Faixa segunda-feira (manhã) com baixa confiabilidade (50%, 2 amostra(s)): blocos profundos aceitarão apenas tarefas interrompíveis (INTERROMPIVEL).");
    }
}
