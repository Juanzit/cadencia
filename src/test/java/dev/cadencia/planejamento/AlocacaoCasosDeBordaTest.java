package dev.cadencia.planejamento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.exportacao.MarkdownPlanoSemanalRepository;
import dev.cadencia.exportacao.PlanoSemanalGerado;
import dev.cadencia.exportacao.PlanoSemanalService;
import dev.cadencia.exportacao.strategies.MarkdownSemanalExportador;
import dev.cadencia.planejamento.strategies.PrioridadeAlocadorStrategy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlocacaoCasosDeBordaTest {

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
    void caso1BacklogVazioDevePreservarBlocosLivresEResumoSemLinhaDeAlocacao() throws IOException {
        Path agenda = escreverYaml(tempDir.resolve("agenda-vazia.yaml"), """
                jornada:
                  inicio: "08:00"
                  fim: "12:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "10:00"
                    fim: "11:00"
                    tipo: REUNIAO
                    titulo: "Alinhamento"
                """);
        Path backlog = escreverYaml(tempDir.resolve("backlog-vazio.yaml"), "tarefas: []\n");

        PlanoSemanalGerado gerado = executarFluxoCompleto(agenda, backlog, tempDir.resolve("dados-1"));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        assertThat(gerado.resultadoAlocacao()).isNotNull();
        assertThat(gerado.resultadoAlocacao().alocados()).isEmpty();
        assertThat(gerado.resultadoAlocacao().naoAlocados()).isEmpty();
        assertThat(markdown)
                .contains("""
                        ## Resumo
                        - Blocos livres: 8 (profundo: 7 · interrompível: 1 · vigilância: 0)
                        - Tempo livre total: 27h00
                        - Turmas em transição: nenhuma
                        """)
                .contains(
                        "| 08:00–10:00 | **Bloco profundo** | Disponível (2h00) |",
                        "| 11:00–12:00 | Bloco interrompível | Disponível (1h00) |",
                        "- [ ] seg 08:00–10:00 · Bloco profundo (2h00)",
                        "- [ ] seg 11:00–12:00 · Bloco interrompível (1h00)")
                .doesNotContain("Pedaços alocados");
    }

    @Test
    void caso2TarefaMaiorQueTodosOsBlocosDeveIrParaNaoAlocadosEAvisosNoMarkdown() throws IOException {
        Path agenda = escreverYaml(tempDir.resolve("agenda-curta.yaml"), """
                jornada:
                  inicio: "08:00"
                  fim: "09:30"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos: []
                """);
        Path backlog = escreverYaml(tempDir.resolve("backlog-gigante.yaml"), """
                tarefas:
                  - id: "projeto-extenso"
                    titulo: "Projeto Monolítico"
                    duracao_total_min: 240
                    tipos_aceitos: [PROFUNDO]
                    tamanho_pedaco_min: 120
                    prioridade: 1
                """);

        PlanoSemanalGerado gerado = executarFluxoCompleto(agenda, backlog, tempDir.resolve("dados-2"));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        assertThat(gerado.resultadoAlocacao().alocados()).isEmpty();
        assertThat(gerado.resultadoAlocacao().naoAlocados()).hasSize(2);
        assertThat(markdown)
                .doesNotContain("Pedaços alocados")
                .contains(
                        "| 08:00–09:30 | **Bloco profundo** | Disponível (1h30) |",
                        "- [ ] seg 08:00–09:30 · Bloco profundo (1h30)",
                        "Não foi possível alocar todos os pedaços de \"Projeto Monolítico\" (1/2, 2/2)");
    }

    @Test
    void caso3NenhumBlocoCompativelDeveRegistrarAvisoSemAlocarEmTipoIncompativel() throws IOException {
        Path agenda = escreverYaml(tempDir.resolve("agenda-sem-profundo.yaml"), """
                jornada:
                  inicio: "08:00"
                  fim: "10:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "08:45"
                    fim: "10:00"
                    tipo: PLANTAO
                    titulo: "Plantão manhã"
                  - dia: TERCA
                    inicio: "08:00"
                    fim: "10:00"
                    tipo: REUNIAO
                    titulo: "Alinhamentos"
                  - dia: QUARTA
                    inicio: "08:00"
                    fim: "10:00"
                    tipo: REUNIAO
                    titulo: "Alinhamentos"
                  - dia: QUINTA
                    inicio: "08:00"
                    fim: "10:00"
                    tipo: REUNIAO
                    titulo: "Alinhamentos"
                  - dia: SEXTA
                    inicio: "08:00"
                    fim: "10:00"
                    tipo: REUNIAO
                    titulo: "Alinhamentos"
                  - dia: SABADO
                    inicio: "08:00"
                    fim: "10:00"
                    tipo: REUNIAO
                    titulo: "Alinhamentos"
                  - dia: DOMINGO
                    inicio: "08:00"
                    fim: "10:00"
                    tipo: REUNIAO
                    titulo: "Alinhamentos"
                """);
        Path backlog = escreverYaml(tempDir.resolve("backlog-profundo.yaml"), """
                tarefas:
                  - id: "spring-novo"
                    titulo: "Spring Boot Avançado"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """);

        PlanoSemanalGerado gerado = executarFluxoCompleto(agenda, backlog, tempDir.resolve("dados-3"));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        assertThat(gerado.resultadoAlocacao().alocados()).isEmpty();
        assertThat(gerado.resultadoAlocacao().naoAlocados()).hasSize(2);
        assertThat(markdown)
                .contains(
                        "| 08:00–08:45 | Bloco interrompível | Disponível (0h45) |",
                        "| 08:45–10:00 | Bloco de vigilância | PLANTAO · Plantão manhã |",
                        "Não foi possível alocar todos os pedaços de \"Spring Boot Avançado\" (1/2, 2/2)")
                .doesNotContain("Spring Boot Avançado (1/2)");
    }

    @Test
    void caso4PrioridadesIguaisDevemPreservarOrdemDeclaradaNoBacklogYaml() throws IOException {
        Path agenda = escreverYaml(tempDir.resolve("agenda-empate.yaml"), """
                jornada:
                  inicio: "08:00"
                  fim: "09:15"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 60
                  interrompivel_minimo_min: 25
                compromissos: []
                """);
        Path backlog = escreverYaml(tempDir.resolve("backlog-empate.yaml"), """
                tarefas:
                  - id: "z-primeiro"
                    titulo: "Z Primeiro do Roadmap"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 2
                  - id: "a-segundo"
                    titulo: "A Segundo do Roadmap"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 2
                  - id: "m-terceiro"
                    titulo: "M Terceiro do Roadmap"
                    duracao_total_min: 25
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 2
                """);

        PlanoSemanalGerado gerado = executarFluxoCompleto(agenda, backlog, tempDir.resolve("dados-4"));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        assertThat(gerado.resultadoAlocacao().alocados()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(markdown).contains("""
                ## Segunda-feira, 23/11
                | Horário | Tipo | Detalhe |
                | :--- | :--- | :--- |
                | 08:00–08:25 | **Bloco profundo** | Z Primeiro do Roadmap (1/1) |
                | 08:25–08:50 | **Bloco profundo** | A Segundo do Roadmap (1/1) |
                | 08:50–09:15 | **Bloco profundo** | M Terceiro do Roadmap (1/1) |
                """);
    }

    @Test
    void caso5Tarefa65MinutosDeveDividirEm25_25_15EEncaixarUltimoPedacoEmJanelaMenor() throws IOException {
        Path agenda = escreverYaml(tempDir.resolve("agenda-65min.yaml"), """
                jornada:
                  inicio: "07:00"
                  fim: "21:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 50
                  interrompivel_minimo_min: 20
                compromissos:
                  - dia: SEGUNDA
                    inicio: "08:00"
                    fim: "20:00"
                    tipo: REUNIAO
                    titulo: "Turno integral"
                  - dia: SEGUNDA
                    inicio: "20:35"
                    fim: "21:00"
                    tipo: REUNIAO
                    titulo: "Fechamento"
                """);
        Path backlog = escreverYaml(tempDir.resolve("backlog-65min.yaml"), """
                tarefas:
                  - id: "collections-65"
                    titulo: "Java Collections"
                    duracao_total_min: 65
                    tipos_aceitos: [PROFUNDO, INTERROMPIVEL]
                    prioridade: 1
                """);

        PlanoSemanalGerado gerado = executarFluxoCompleto(agenda, backlog, tempDir.resolve("dados-5"));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        List<PedacoAlocado> alocados = gerado.resultadoAlocacao().alocados();
        assertThat(alocados).hasSize(3);
        assertThat(alocados.get(0).pedaco().duracao().toMinutes()).isEqualTo(25);
        assertThat(alocados.get(1).pedaco().duracao().toMinutes()).isEqualTo(25);
        assertThat(alocados.get(2).pedaco().duracao().toMinutes()).isEqualTo(15);

        assertThat(markdown).contains(
                "- Pedaços alocados: 3 (1h05)",
                "| 07:00–07:25 | **Bloco profundo** | Java Collections (1/3) |",
                "| 07:25–07:50 | **Bloco profundo** | Java Collections (2/3) |",
                "| 20:10–20:25 | Bloco interrompível | Java Collections (3/3) |",
                "- [ ] seg 07:00–07:25 · Java Collections (1/3)",
                "- [ ] seg 07:25–07:50 · Java Collections (2/3)",
                "- [ ] seg 20:10–20:25 · Java Collections (3/3)");
    }

    @Test
    void caso6FolgaMinima10MinutosAntesEDepoisDeTreinoEAulaSemSobreposicao() throws IOException {
        Path agenda = escreverYaml(tempDir.resolve("agenda-folga.yaml"), """
                jornada:
                  inicio: "07:00"
                  fim: "13:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 60
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "08:00"
                    fim: "09:00"
                    tipo: TREINO
                    titulo: "Treino de força"
                  - dia: SEGUNDA
                    inicio: "11:00"
                    fim: "12:00"
                    tipo: AULA
                    titulo: "Aula Java"
                """);
        Path backlog = escreverYaml(tempDir.resolve("backlog-folga.yaml"), """
                tarefas:
                  - id: "java-200"
                    titulo: "Java Backend"
                    duracao_total_min: 200
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """);

        PlanoSemanalGerado gerado = executarFluxoCompleto(agenda, backlog, tempDir.resolve("dados-6"));
        String markdown = Files.readString(gerado.arquivoSaida(), StandardCharsets.UTF_8);

        List<PedacoAlocado> segundaAlocados = gerado.resultadoAlocacao().alocados().stream()
                .filter(alocado -> alocado.data().equals(LocalDate.of(2026, 11, 23)))
                .toList();
        assertThat(segundaAlocados).hasSize(8);

        for (int i = 1; i < segundaAlocados.size(); i++) {
            assertThat(segundaAlocados.get(i).inicio()).isAfterOrEqualTo(segundaAlocados.get(i - 1).fim());
        }

        assertThat(markdown).contains("""
                ## Segunda-feira, 23/11
                | Horário | Tipo | Detalhe |
                | :--- | :--- | :--- |
                | 07:00–07:25 | **Bloco profundo** | Java Backend (1/8) |
                | 07:25–07:50 | **Bloco profundo** | Java Backend (2/8) |
                | 08:00–09:00 | Treino | Treino de força |
                | 09:10–09:35 | **Bloco profundo** | Java Backend (3/8) |
                | 09:35–10:00 | **Bloco profundo** | Java Backend (4/8) |
                | 10:00–10:25 | **Bloco profundo** | Java Backend (5/8) |
                | 10:25–10:50 | **Bloco profundo** | Java Backend (6/8) |
                | 11:00–12:00 | Aula | Aula Java |
                | 12:10–12:35 | **Bloco profundo** | Java Backend (7/8) |
                | 12:35–13:00 | **Bloco profundo** | Java Backend (8/8) |
                """);
    }

    @Test
    void deveGerarSemana2026W48IntegrandoExemplosOficiaisComIdempotencia() throws IOException {
        Path agendaExemplo = Path.of("exemplos/agenda.exemplo.yaml");
        Path backlogExemplo = Path.of("exemplos/backlog.exemplo.yaml");

        YamlBacklogRepository backlogRepo = new YamlBacklogRepository(yamlMapper, validator);
        List<Tarefa> tarefasExemplo = backlogRepo.carregar(backlogExemplo);
        assertThat(tarefasExemplo).hasSize(7);

        Path dadosDir = tempDir.resolve("dados-exemplo");
        PlanoSemanalGerado primeira = executarFluxoCompleto(agendaExemplo, backlogExemplo, dadosDir);
        byte[] bytesPrimeira = Files.readAllBytes(primeira.arquivoSaida());

        PlanoSemanalGerado segunda = executarFluxoCompleto(agendaExemplo, backlogExemplo, dadosDir);
        byte[] bytesSegunda = Files.readAllBytes(segunda.arquivoSaida());

        assertThat(primeira.arquivoSaida()).isEqualTo(dadosDir.resolve("semana/2026-W48.md"));
        assertThat(bytesSegunda).isEqualTo(bytesPrimeira);
        assertThat(primeira.resultadoAlocacao().naoAlocados()).isEmpty();
        assertThat(primeira.resultadoAlocacao().alocados())
                .allSatisfy(alocado -> assertThat(alocado.pedaco().tarefa().tiposAceitos())
                        .contains(alocado.blocoOrigem().tipo()));

        String markdown = new String(bytesPrimeira, StandardCharsets.UTF_8);
        assertThat(markdown)
                .startsWith("# Semana 2026-W48 (23/11 a 29/11)")
                .doesNotContain("\r")
                .contains(
                        "- Pedaços alocados:",
                        "Java Core: Streams, Lambdas e Records (1/4)",
                        "Java Collections e Complexidade (6/6)",
                        "Spring Boot: Web, Validação e Arquitetura (1/6)",
                        "JPA e Hibernate: Mapeamento e Consultas (4/4)",
                        "Testes com JUnit 5, AssertJ e Mockito (4/4)",
                        "SQL e Modelagem Relacional (3/3)",
                        "Flashcards e Revisão Leve de Conceitos Java (3/3)",
                        "## Checklist de estudos",
                        "## Avisos\n- Nenhum aviso.\n");
    }

    private PlanoSemanalGerado executarFluxoCompleto(
            Path caminhoAgenda, Path caminhoBacklog, Path diretorioDados) {
        AgendaRepository agendaRepository = new YamlAgendaRepository(yamlMapper, validator);
        AgendaService agendaService = new AgendaService(agendaRepository, RELOGIO_W48);
        BacklogRepository backlogRepository = new YamlBacklogRepository(yamlMapper, validator);
        PlanejamentoService planejamentoService = new PlanejamentoService(new PrioridadeAlocadorStrategy());
        PlanoSemanalService planoSemanalService = new PlanoSemanalService(
                agendaService,
                backlogRepository,
                planejamentoService,
                new MarkdownSemanalExportador(),
                new MarkdownPlanoSemanalRepository(),
                diretorioDados);
        return planoSemanalService.gerarEExportar(caminhoAgenda, caminhoBacklog, null);
    }

    private static Path escreverYaml(Path destino, String conteudo) throws IOException {
        Files.writeString(destino, conteudo, StandardCharsets.UTF_8);
        return destino;
    }
}
