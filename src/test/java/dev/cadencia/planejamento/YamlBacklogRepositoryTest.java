package dev.cadencia.planejamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlBacklogRepositoryTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private static ObjectMapper yamlMapper;
    private static BacklogRepository repository;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void configurarRepositorio() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        yamlMapper = new ObjectMapper(new YAMLFactory());
        repository = new YamlBacklogRepository(yamlMapper, validator);
    }

    @AfterAll
    static void fecharValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void deveCarregarBacklogValidoAplicandoFallbackDe25MinutosEPreservandoOrdemEImutabilidade()
            throws IOException {
        Path arquivo = tempDir.resolve("backlog.yaml");
        Files.writeString(arquivo, """
                tarefas:
                  - id: " java-collections "
                    titulo: " Java Collections "
                    duracao_total_min: 150
                    tipos_aceitos:
                      - PROFUNDO
                      - INTERROMPIVEL
                    prioridade: 1
                  - id: "flashcards-java"
                    titulo: "Flashcards Java"
                    duracao_total_min: 50
                    tipos_aceitos:
                      - INTERROMPIVEL
                      - VIGILANCIA
                    tamanho_pedaco_min: 15
                    prioridade: 2
                """, StandardCharsets.UTF_8);

        List<Tarefa> tarefas = repository.carregar(arquivo);

        assertThat(tarefas).hasSize(2);
        Tarefa primeira = tarefas.get(0);
        assertThat(primeira.id()).isEqualTo("java-collections");
        assertThat(primeira.titulo()).isEqualTo("Java Collections");
        assertThat(primeira.duracaoTotal()).isEqualTo(Duration.ofMinutes(150));
        assertThat(primeira.tiposAceitos()).containsExactly(TipoBloco.PROFUNDO, TipoBloco.INTERROMPIVEL);
        assertThat(primeira.tamanhoPedaco()).isEqualTo(Duration.ofMinutes(25));
        assertThat(primeira.prioridade()).isEqualTo(1);
        assertThat(primeira.dividirEmPedacos()).hasSize(6);

        Tarefa segunda = tarefas.get(1);
        assertThat(segunda.id()).isEqualTo("flashcards-java");
        assertThat(segunda.tamanhoPedaco()).isEqualTo(Duration.ofMinutes(15));
        assertThat(segunda.tiposAceitos()).containsExactly(TipoBloco.INTERROMPIVEL, TipoBloco.VIGILANCIA);
        assertThat(segunda.prioridade()).isEqualTo(2);

        assertThatThrownBy(tarefas::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void devePermitirBacklogComListaDeTarefasVazia() throws IOException {
        Path arquivo = tempDir.resolve("backlog-vazio.yaml");
        Files.writeString(arquivo, "tarefas: []\n", StandardCharsets.UTF_8);

        List<Tarefa> tarefas = repository.carregar(arquivo);

        assertThat(tarefas).isEmpty();
        assertThatThrownBy(tarefas::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveInformarCaminhoEOrientacaoQuandoArquivoNaoExistir() {
        Path inexistente = tempDir.resolve("backlog-inexistente.yaml");

        assertThatThrownBy(() -> repository.carregar(inexistente))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining(inexistente.toString())
                .hasMessageContaining("inexistente")
                .hasMessageContaining("Verifique o caminho");
    }

    @Test
    void deveRejeitarArquivoYamlVazioOuNulo() throws IOException {
        Path yamlNulo = tempDir.resolve("nulo.yaml");
        Files.writeString(yamlNulo, "~\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(yamlNulo))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(yamlNulo.toString())
                .hasMessageContaining("vazio ou nulo");

        Path yamlEmBranco = tempDir.resolve("em-branco.yaml");
        Files.writeString(yamlEmBranco, "", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(yamlEmBranco))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(yamlEmBranco.toString())
                .hasMessageContaining("vazio ou nulo");
    }

    @Test
    void deveInformarArquivoLinhaEColunaParaErroDeSintaxeETipoIncompativelYaml() throws IOException {
        Path sintaxe = tempDir.resolve("sintaxe-invalida.yaml");
        Files.writeString(sintaxe, """
                tarefas:
                  - id: "t1"
                    titulo: [quebrado
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(sintaxe))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(JsonProcessingException.class)
                .hasMessageContaining(sintaxe.toString())
                .hasMessageContaining("linha ")
                .hasMessageContaining("coluna ")
                .hasMessageContaining("indentação");

        Path tipoIncompativel = tempDir.resolve("tipo-incompativel.yaml");
        Files.writeString(tipoIncompativel, """
                tarefas:
                  - id: "t1"
                    titulo: "Java"
                    duracao_total_min: "cem"
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(tipoIncompativel))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(JsonProcessingException.class)
                .hasMessageContaining(tipoIncompativel.toString())
                .hasMessageContaining("linha ")
                .hasMessageContaining("coluna ");
    }

    @Test
    void deveInformarCaminhosDosCamposSemLinhaParaViolacoesDeValidacao() throws IOException {
        Path semTarefas = tempDir.resolve("sem-tarefas.yaml");
        Files.writeString(semTarefas, "{}\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(semTarefas))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains(semTarefas.toString())
                        .contains("campo 'tarefas'")
                        .doesNotContain("linha")
                        .doesNotContain("coluna"));

        Path invalido = tempDir.resolve("campos-invalidos.yaml");
        Files.writeString(invalido, """
                tarefas:
                  - id: "  "
                    titulo: ""
                    duracao_total_min: 0
                    tipos_aceitos: []
                    tamanho_pedaco_min: -5
                    prioridade: 0
                  - id: "t2"
                    titulo: "Spring Boot"
                    duracao_total_min: 120
                    tipos_aceitos:
                      - DESCONHECIDO
                    prioridade: -1
                  - ~
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(invalido))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains(invalido.toString())
                        .contains("tarefas[0].id")
                        .contains("tarefas[0].titulo")
                        .contains("tarefas[0].duracaoTotalMin")
                        .contains("tarefas[0].tiposAceitos")
                        .contains("tarefas[0].tamanhoPedacoMin")
                        .contains("tarefas[0].prioridade")
                        .contains("tarefas[1].tiposAceitos")
                        .contains("tarefas[1].prioridade")
                        .contains("tarefas[2]")
                        .doesNotContain("linha")
                        .doesNotContain("coluna"));
    }

    @Test
    void deveRejeitarIdsDuplicadosInformandoCampoDaTarefaDuplicada() throws IOException {
        Path duplicados = tempDir.resolve("ids-duplicados.yaml");
        Files.writeString(duplicados, """
                tarefas:
                  - id: "java-core"
                    titulo: "Java Core"
                    duracao_total_min: 100
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: " java-core "
                    titulo: "Java Core Repetido"
                    duracao_total_min: 50
                    tipos_aceitos: [INTERROMPIVEL]
                    prioridade: 2
                  - id: "jpa-hibernate"
                    titulo: "JPA e Hibernate"
                    duracao_total_min: 75
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 3
                  - id: "java-core"
                    titulo: "Java Core Repetido 2"
                    duracao_total_min: 25
                    tipos_aceitos: [VIGILANCIA]
                    prioridade: 4
                  - id: "  "
                    titulo: "Sem id válido"
                    duracao_total_min: 25
                    tipos_aceitos: [VIGILANCIA]
                    prioridade: 5
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(duplicados))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains(duplicados.toString())
                        .contains("campo 'tarefas[1].id':")
                        .contains("campo 'tarefas[3].id':")
                        .doesNotContain("tarefas[0].id")
                        .doesNotContain("tarefas[2].id")
                        .doesNotContain("tarefas.id[")
                        .doesNotContain("campo 'tarefas.id':")
                        .contains("duplicado")
                        .contains("java-core")
                        .doesNotContain("linha")
                        .doesNotContain("coluna"));
    }

    @Test
    void deveValidarParametrosNulosNoConstrutorENoMetodoCarregar() {
        assertThatNullPointerException().isThrownBy(() -> new YamlBacklogRepository(null, validator));
        assertThatNullPointerException().isThrownBy(() -> new YamlBacklogRepository(yamlMapper, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new YamlBacklogRepository(yamlMapper, validator, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new YamlBacklogRepository(yamlMapper, validator, Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new YamlBacklogRepository(yamlMapper, validator, Duration.ofMinutes(-5)));
        assertThatNullPointerException().isThrownBy(() -> repository.carregar(null));
        assertThatNullPointerException().isThrownBy(() -> repository.carregarSeExistir(null));
    }

    @Test
    void deveAplicarTamanhoPedacoPadraoConfiguradoQuandoOmitidoNoYaml() throws IOException {
        Path arquivo = tempDir.resolve("backlog-pedaco-configurado.yaml");
        Files.writeString(arquivo, """
                tarefas:
                  - id: "sem-pedaco"
                    titulo: "Sem tamanho explícito"
                    duracao_total_min: 120
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                  - id: "com-pedaco"
                    titulo: "Com tamanho explícito"
                    duracao_total_min: 60
                    tipos_aceitos: [INTERROMPIVEL]
                    tamanho_pedaco_min: 15
                    prioridade: 2
                """, StandardCharsets.UTF_8);

        BacklogRepository repoPedaco40 = new YamlBacklogRepository(
                yamlMapper, validator, Duration.ofMinutes(40));
        List<Tarefa> tarefas = repoPedaco40.carregar(arquivo);

        assertThat(tarefas).hasSize(2);
        assertThat(tarefas.get(0).tamanhoPedaco()).isEqualTo(Duration.ofMinutes(40));
        assertThat(tarefas.get(0).dividirEmPedacos()).hasSize(3);
        assertThat(tarefas.get(1).tamanhoPedaco()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void deveRetornarVazioQuandoArquivoOpcionalNaoExistirECarregarQuandoPresente() throws IOException {
        Path inexistente = tempDir.resolve("nao-existe.yaml");
        assertThat(repository.carregarSeExistir(inexistente)).isEmpty();

        Path existente = tempDir.resolve("backlog-opcional.yaml");
        Files.writeString(existente, """
                tarefas:
                  - id: "java-core"
                    titulo: "Java Core"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        assertThat(repository.carregarSeExistir(existente))
                .isPresent()
                .get()
                .satisfies(lista -> {
                    assertThat(lista).hasSize(1);
                    assertThat(lista.get(0).id()).isEqualTo("java-core");
                });
    }
}
