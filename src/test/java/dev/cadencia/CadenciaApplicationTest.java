package dev.cadencia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cadencia.cli.CadenciaCommand;
import dev.cadencia.cli.PicocliCommandRunner;
import dev.cadencia.cli.PicocliSpringFactory;
import dev.cadencia.cli.SemanaCommand;
import dev.cadencia.exportacao.PlanoSemanalService;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.strategies.AlocadorStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
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
        assertThat(applicationContext.getBean(AlocadorStrategy.class)).isNotNull();
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
}
