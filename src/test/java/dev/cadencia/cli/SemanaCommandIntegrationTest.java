package dev.cadencia.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cadencia.exportacao.PlanoSemanalService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

@SpringBootTest
class SemanaCommandIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static Path diretorioDadosTemporario;

    @Autowired
    private PicocliCommandRunner runner;

    @Autowired
    private PicocliSpringFactory springFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void configurarDiretorioDadosTemporario(DynamicPropertyRegistry registry) throws IOException {
        diretorioDadosTemporario = Files.createTempDirectory("cadencia-cli-test-");
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
    void deveExibirAjudaComExitCodeZeroQuandoExecutadoSemSubcomando() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int codigo = runner.executar(new PrintWriter(out), new PrintWriter(err));

        assertThat(codigo).isZero();
        assertThat(runner.getExitCode()).isZero();
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString()).contains("cadencia", "semana");
    }

    @Test
    void deveGerarESobrescreverPlanoUsandoAgendaPadraoERelogioQuandoOpcoesForemOmitidas()
            throws IOException {
        Path agendaPadrao = diretorioDadosTemporario.resolve("agenda.yaml");
        Files.copy(
                Path.of("exemplos/agenda.exemplo.yaml"),
                agendaPadrao,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path destinoEsperado = diretorioDadosTemporario.resolve("semana/2026-W48.md");
        Files.createDirectories(destinoEsperado.getParent());
        Files.writeString(destinoEsperado, "ARQUIVO ANTIGO\r\n", StandardCharsets.UTF_8);

        StringWriter out1 = new StringWriter();
        StringWriter err1 = new StringWriter();
        int codigo1 = runner.executar(new PrintWriter(out1), new PrintWriter(err1), "semana");

        assertThat(codigo1).isZero();
        assertThat(err1.toString()).isEmpty();
        assertThat(out1.toString()).contains("2026-W48", destinoEsperado.toString(), "24 blocos livres");

        byte[] primeiraGravacao = Files.readAllBytes(destinoEsperado);
        String conteudo1 = new String(primeiraGravacao, StandardCharsets.UTF_8);
        assertThat(conteudo1)
                .startsWith("# Semana 2026-W48 (23/11 a 29/11)")
                .doesNotContain("ARQUIVO ANTIGO")
                .doesNotContain("\r");

        StringWriter out2 = new StringWriter();
        StringWriter err2 = new StringWriter();
        int codigo2 = runner.executar(new PrintWriter(out2), new PrintWriter(err2), "semana");

        assertThat(codigo2).isZero();
        assertThat(Files.readAllBytes(destinoEsperado)).isEqualTo(primeiraGravacao);
    }

    @Test
    void deveUsarAgendaAlternativaEDataExplicitaInclusiveNaViradaDeAnoIso() throws IOException {
        StringWriter outOutraSemana = new StringWriter();
        StringWriter errOutraSemana = new StringWriter();
        int codigoW50 = runner.executar(
                new PrintWriter(outOutraSemana),
                new PrintWriter(errOutraSemana),
                "semana",
                "--agenda", "exemplos/agenda.exemplo.yaml",
                "--data", "2026-12-09");

        Path saidaW50 = diretorioDadosTemporario.resolve("semana/2026-W50.md");
        assertThat(codigoW50).isZero();
        assertThat(errOutraSemana.toString()).isEmpty();
        assertThat(Files.readString(saidaW50, StandardCharsets.UTF_8))
                .startsWith("# Semana 2026-W50 (07/12 a 13/12)")
                .doesNotContain("\r");

        StringWriter outVirada = new StringWriter();
        StringWriter errVirada = new StringWriter();
        int codigoVirada = runner.executar(
                new PrintWriter(outVirada),
                new PrintWriter(errVirada),
                "semana",
                "--data", "2025-12-31",
                "--agenda", "exemplos/agenda.exemplo.yaml");

        Path saidaW01 = diretorioDadosTemporario.resolve("semana/2026-W01.md");
        assertThat(codigoVirada).isZero();
        assertThat(errVirada.toString()).isEmpty();
        assertThat(Files.readString(saidaW01, StandardCharsets.UTF_8))
                .startsWith("# Semana 2026-W01 (29/12 a 04/01)")
                .doesNotContain("\r");
    }

    @Test
    void deveRetornarCodigo2SemStackTraceParaAgendaInexistenteYamlInvalidoDadosInvalidosEDataMalformada()
            throws IOException {
        // 1. Agenda inexistente (mesmo com --verbose, erro de dados retorna 2 sem stack trace)
        StringWriter outInex = new StringWriter();
        StringWriter errInex = new StringWriter();
        int codInex = runner.executar(
                new PrintWriter(outInex),
                new PrintWriter(errInex),
                "semana",
                "--verbose",
                "--agenda", diretorioDadosTemporario.resolve("nao-existe.yaml").toString());
        assertThat(codInex).isEqualTo(2);
        assertThat(errInex.toString())
                .contains("nao-existe.yaml")
                .doesNotContain("at dev.cadencia.")
                .doesNotContain("DadosInvalidosException");

        // 2. Erro de sintaxe YAML
        Path yamlSintaxe = Files.createTempFile(diretorioDadosTemporario, "sintaxe-", ".yaml");
        Files.writeString(yamlSintaxe, "jornada:\n  inicio: [invalido\n", StandardCharsets.UTF_8);
        StringWriter errSintaxe = new StringWriter();
        int codSintaxe = runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errSintaxe),
                "semana", "--agenda", yamlSintaxe.toString());
        assertThat(codSintaxe).isEqualTo(2);
        assertThat(errSintaxe.toString())
                .contains("linha", "coluna")
                .doesNotContain("at dev.cadencia.");

        // 3. Erro de validação de campo YAML
        Path yamlCampoInvalido = Files.createTempFile(diretorioDadosTemporario, "validacao-", ".yaml");
        Files.writeString(yamlCampoInvalido, """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 30
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "09:00"
                    fim: "25:00"
                    tipo: REUNIAO
                    titulo: "Inválida"
                """, StandardCharsets.UTF_8);
        StringWriter errValidacao = new StringWriter();
        int codValidacao = runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errValidacao),
                "semana", "--agenda", yamlCampoInvalido.toString());
        assertThat(codValidacao).isEqualTo(2);
        assertThat(errValidacao.toString())
                .contains("compromissos[0].fim")
                .doesNotContain("at dev.cadencia.");

        // 4. Data malformada (formato e dia inexistente no calendário), --data em branco, --agenda em branco e flag desconhecida
        for (String dataRuim : new String[] {"2026-02-30", "23/11/2026", ""}) {
            StringWriter errData = new StringWriter();
            int codData = runner.executar(
                    new PrintWriter(new StringWriter()),
                    new PrintWriter(errData),
                    "--verbose", "semana", "--data", dataRuim);
            assertThat(codData).isEqualTo(2);
            assertThat(errData.toString())
                    .contains("--data", "AAAA-MM-DD")
                    .doesNotContain("at dev.cadencia.");
        }

        StringWriter errAgendaVazia = new StringWriter();
        int codAgendaVazia = runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errAgendaVazia),
                "semana", "--agenda", "   ");
        assertThat(codAgendaVazia).isEqualTo(2);
        assertThat(errAgendaVazia.toString()).contains("--agenda").doesNotContain("at dev.cadencia.");

        StringWriter errParametro = new StringWriter();
        int codParametro = runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errParametro),
                "semana", "--opcao-desconhecida");
        assertThat(codParametro).isEqualTo(2);
        assertThat(errParametro.toString()).contains("Argumentos inválidos").doesNotContain("at dev.cadencia.");

        StringWriter errBacklogVazio = new StringWriter();
        int codBacklogVazio = runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errBacklogVazio),
                "semana", "--backlog", "   ");
        assertThat(codBacklogVazio).isEqualTo(2);
        assertThat(errBacklogVazio.toString()).contains("--backlog").doesNotContain("at dev.cadencia.");

        StringWriter errBacklogInex = new StringWriter();
        int codBacklogInex = runner.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errBacklogInex),
                "semana",
                "--agenda", "exemplos/agenda.exemplo.yaml",
                "--backlog", diretorioDadosTemporario.resolve("backlog-ausente.yaml").toString());
        assertThat(codBacklogInex).isEqualTo(2);
        assertThat(errBacklogInex.toString()).contains("backlog-ausente.yaml").doesNotContain("at dev.cadencia.");
    }

    @Test
    void deveGerarPlanoComEstudosAlocadosQuandoOpcaoBacklogForInformada() throws IOException {
        Path backlogExplicito = Files.createTempFile(diretorioDadosTemporario, "backlog-", ".yaml");
        Files.writeString(backlogExplicito, """
                tarefas:
                  - id: "collections"
                    titulo: "Java Collections"
                    duracao_total_min: 150
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: "impossivel"
                    titulo: "Tarefa Gigante"
                    duracao_total_min: 50000
                    tipos_aceitos: [VIGILANCIA]
                    prioridade: 2
                """, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int codigo = runner.executar(
                new PrintWriter(out),
                new PrintWriter(err),
                "semana",
                "--agenda", "exemplos/agenda.exemplo.yaml",
                "--backlog", backlogExplicito.toString(),
                "--data", "2026-11-23");

        assertThat(codigo).isZero();
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString())
                .contains("2026-W48", "24 blocos livres", "pedaço(s) alocado(s)")
                .contains("não couberam nesta semana");

        Path destino = diretorioDadosTemporario.resolve("semana/2026-W48.md");
        String markdown = Files.readString(destino, StandardCharsets.UTF_8);
        assertThat(markdown)
                .contains("| 07:00–07:25 | **Bloco profundo** | Java Collections (1/6) |")
                .contains("- [ ] seg 07:00–07:25 · Java Collections (1/6)")
                .contains("Tarefa Gigante");
    }

    @Test
    void deveRetornarCodigo1EExibirStackTraceSomenteComVerboseEmErroInesperado() {
        PlanoSemanalService servicoQueFalha = new PlanoSemanalService(
                applicationContext.getBean(dev.cadencia.agenda.AgendaService.class),
                applicationContext.getBean(dev.cadencia.exportacao.strategies.ExportadorStrategy.class),
                (destino, conteudo) -> {
                    throw new IllegalStateException("Falha simulada de sistema");
                },
                diretorioDadosTemporario);

        GenericApplicationContext contextoComFalha = new GenericApplicationContext();
        contextoComFalha.registerBean(CadenciaCommand.class, CadenciaCommand::new);
        contextoComFalha.registerBean(SemanaCommand.class, () -> new SemanaCommand(servicoQueFalha));
        contextoComFalha.refresh();

        PicocliCommandRunner runnerComFalha = new PicocliCommandRunner(new PicocliSpringFactory(contextoComFalha));

        // Sem --verbose: código 1, mensagem amigável, sem stack trace
        StringWriter errSemVerbose = new StringWriter();
        int codSemVerbose = runnerComFalha.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errSemVerbose),
                "semana", "--agenda", "exemplos/agenda.exemplo.yaml");
        assertThat(codSemVerbose).isEqualTo(1);
        assertThat(errSemVerbose.toString())
                .contains("Erro inesperado: Falha simulada de sistema", "--verbose")
                .doesNotContain("java.lang.IllegalStateException");

        // Com --verbose após subcomando
        StringWriter errComVerboseSub = new StringWriter();
        int codComVerboseSub = runnerComFalha.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errComVerboseSub),
                "semana", "--verbose", "--agenda", "exemplos/agenda.exemplo.yaml");
        assertThat(codComVerboseSub).isEqualTo(1);
        assertThat(errComVerboseSub.toString())
                .contains("Erro inesperado: Falha simulada de sistema")
                .contains("java.lang.IllegalStateException: Falha simulada de sistema");

        // Com --verbose antes do subcomando
        StringWriter errComVerboseRaiz = new StringWriter();
        int codComVerboseRaiz = runnerComFalha.executar(
                new PrintWriter(new StringWriter()),
                new PrintWriter(errComVerboseRaiz),
                "--verbose", "semana", "--agenda", "exemplos/agenda.exemplo.yaml");
        assertThat(codComVerboseRaiz).isEqualTo(1);
        assertThat(errComVerboseRaiz.toString())
                .contains("java.lang.IllegalStateException: Falha simulada de sistema");

        // Também valida a propagação via SpringApplication.exit em contexto isolado sem fechar o contexto do teste
        contextoComFalha.registerBean(PicocliCommandRunner.class, () -> runnerComFalha);
        int codigoViaSpringExit = SpringApplication.exit(contextoComFalha);
        assertThat(codigoViaSpringExit).isEqualTo(1);
    }

    @Test
    void picocliSpringFactoryDeveFazerFallbackParaClassesNaoGerenciadas() throws Exception {
        CommandLine.IFactory defaultResultTarget = springFactory;
        Object helper = defaultResultTarget.create(PicocliHelperNaoBean.class);
        assertThat(helper).isInstanceOf(PicocliHelperNaoBean.class);
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

    static class PicocliHelperNaoBean {
        PicocliHelperNaoBean() {
        }
    }
}
