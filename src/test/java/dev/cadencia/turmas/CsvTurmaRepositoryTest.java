package dev.cadencia.turmas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.Turma;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTurmaRepositoryTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private static TurmaRepository repository;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void configurarRepositorio() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        repository = new CsvTurmaRepository(validator);
    }

    @AfterAll
    static void fecharValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void deveCarregarCsvValidoComBomAspasEscapadasEspacosELinhasEmBrancoPreservandoOrdemEImutabilidade()
            throws IOException {
        Path arquivo = tempDir.resolve("turmas.csv");
        String conteudo = "\uFEFF"
                + " turma , empresa , modulo_atual , inicio_modulo , duracao_semanas \n"
                + "  T01 , \"Empresa \"\"A\"\", Unidade 1\" , \"Java Core, Módulo 1\" , 2026-11-02 , 4 \n"
                + "\n"
                + "R02,\"EmpresaB\" ,Spring Boot e JPA,2026-11-09,\"6\"\n";
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);

        List<Turma> turmas = repository.carregar(arquivo);

        assertThat(turmas).containsExactly(
                new Turma("T01", "Empresa \"A\", Unidade 1", "Java Core, Módulo 1", LocalDate.of(2026, 11, 2), 4),
                new Turma("R02", "EmpresaB", "Spring Boot e JPA", LocalDate.of(2026, 11, 9), 6));
        assertThatThrownBy(turmas::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void devePermitirCsvComApenasCabecalhoESemLinhasDeDados() throws IOException {
        Path arquivo = tempDir.resolve("turmas-sem-linhas.csv");
        Files.writeString(
                arquivo,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n",
                StandardCharsets.UTF_8);

        List<Turma> turmas = repository.carregar(arquivo);

        assertThat(turmas).isEmpty();
        assertThatThrownBy(turmas::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveVerificarExistenciaDoArquivoEValidarCaminhoNulo() throws IOException {
        Path ausente = tempDir.resolve("nao-existe-ainda.csv");
        assertThat(repository.existe(ausente)).isFalse();

        Path presente = tempDir.resolve("existe.csv");
        Files.writeString(
                presente,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n",
                StandardCharsets.UTF_8);
        assertThat(repository.existe(presente)).isTrue();
    }

    @Test
    void deveCarregarSeExistirRetornandoVazioQuandoAusenteEFalhandoQuandoExistenteInvalido()
            throws IOException {
        Path ausente = tempDir.resolve("nao-existe.csv");
        assertThat(repository.carregarSeExistir(ausente)).isEmpty();

        Path existente = tempDir.resolve("existente.csv");
        Files.writeString(
                existente,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T03,EmpresaA,APIs REST,2026-11-16,4\n",
                StandardCharsets.UTF_8);
        assertThat(repository.carregarSeExistir(existente))
                .isPresent()
                .get()
                .satisfies(lista -> assertThat(lista)
                        .containsExactly(new Turma("T03", "EmpresaA", "APIs REST", LocalDate.of(2026, 11, 16), 4)));

        Path existenteInvalido = tempDir.resolve("existente-invalido.csv");
        Files.writeString(existenteInvalido, "coluna_errada\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregarSeExistir(existenteInvalido))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(existenteInvalido.toString())
                .hasMessageContaining("linha 1");
    }

    @Test
    void deveRejeitarArquivoInexistenteDiretorioOuCodificacaoNaoUtf8() throws IOException {
        Path inexistente = tempDir.resolve("turmas-inexistente.csv");
        assertThatThrownBy(() -> repository.carregar(inexistente))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining(inexistente.toString())
                .hasMessageContaining("linha 1")
                .hasMessageContaining("inexistente")
                .hasMessageContaining("Verifique o caminho");

        Path diretorio = Files.createDirectory(tempDir.resolve("pasta"));
        assertThatThrownBy(() -> repository.carregar(diretorio))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining(diretorio.toString())
                .hasMessageContaining("linha 1");

        Path latin1 = tempDir.resolve("latin1.csv");
        Files.write(
                latin1,
                ("turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Módulo Java,2026-11-02,4\n").getBytes(StandardCharsets.ISO_8859_1));
        assertThatThrownBy(() -> repository.carregar(latin1))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(CharacterCodingException.class)
                .hasMessageContaining(latin1.toString())
                .hasMessageContaining("linha 1")
                .hasMessageContaining("UTF-8");
    }

    @Test
    void deveRejeitarArquivoVazioOuApenasComLinhasEmBranco() throws IOException {
        Path vazio = tempDir.resolve("vazio.csv");
        Files.writeString(vazio, "", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(vazio))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(vazio.toString())
                .hasMessageContaining("linha 1")
                .hasMessageContaining("vazio")
                .hasMessageContaining("turma,empresa,modulo_atual,inicio_modulo,duracao_semanas");

        Path apenasBrancos = tempDir.resolve("brancos.csv");
        Files.writeString(apenasBrancos, "\n   \n\t\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(apenasBrancos))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(apenasBrancos.toString())
                .hasMessageContaining("linha 1")
                .hasMessageContaining("vazio");
    }

    @Test
    void deveRejeitarCabecalhoInvalidoComNumeroDaLinhaCorreto() throws IOException {
        Path cabecalhoErrado = tempDir.resolve("cabecalho-errado.csv");
        Files.writeString(
                cabecalhoErrado,
                "\ncodigo,empresa,modulo_atual,inicio_modulo,duracao_semanas\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(cabecalhoErrado))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(cabecalhoErrado.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("cabeçalho CSV inválido")
                .hasMessageContaining("turma,empresa,modulo_atual,inicio_modulo,duracao_semanas");
    }

    @Test
    void deveRejeitarColunasFaltantesExtrasEAspasMalformadasInformandoLinha() throws IOException {
        Path faltando = tempDir.resolve("colunas-faltando.csv");
        Files.writeString(
                faltando,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(faltando))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(faltando.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("esperado 5, encontrado 4");

        Path extras = tempDir.resolve("colunas-extras.csv");
        Files.writeString(
                extras,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02,4,extra\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(extras))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(extras.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("esperado 5, encontrado 6");

        Path aspasAbertas = tempDir.resolve("aspas-abertas.csv");
        Files.writeString(
                aspasAbertas,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,\"EmpresaA,Java Core,2026-11-02,4\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(aspasAbertas))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(aspasAbertas.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("aspas não fechadas");

        Path conteudoAposAspas = tempDir.resolve("conteudo-apos-aspas.csv");
        Files.writeString(
                conteudoAposAspas,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,\"EmpresaA\"extra,Java Core,2026-11-02,4\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(conteudoAposAspas))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(conteudoAposAspas.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("após fechamento de aspas");

        Path aspasMeioCampo = tempDir.resolve("aspas-meio.csv");
        Files.writeString(
                aspasMeioCampo,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,Emp\"A,Java Core,2026-11-02,4\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(aspasMeioCampo))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(aspasMeioCampo.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("aspas inesperadas");
    }

    @Test
    void deveRejeitarDatasEDuracoesInvalidasInformandoArquivoELinha() throws IOException {
        Path dataInvalida = tempDir.resolve("data-invalida.csv");
        Files.writeString(
                dataInvalida,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02,4\n"
                        + "T02,EmpresaA,Spring,02/11/2026,4\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(dataInvalida))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(DateTimeParseException.class)
                .hasMessageContaining(dataInvalida.toString())
                .hasMessageContaining("linha 3")
                .hasMessageContaining("inicio_modulo")
                .hasMessageContaining("AAAA-MM-DD");

        Path dataImpossivel = tempDir.resolve("data-impossivel.csv");
        Files.writeString(
                dataImpossivel,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-02-30,4\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(dataImpossivel))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(DateTimeParseException.class)
                .hasMessageContaining(dataImpossivel.toString())
                .hasMessageContaining("linha 2");

        Path duracaoNaoNumerica = tempDir.resolve("duracao-texto.csv");
        Files.writeString(
                duracaoNaoNumerica,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02,quatro\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(duracaoNaoNumerica))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(NumberFormatException.class)
                .hasMessageContaining(duracaoNaoNumerica.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("duracao_semanas")
                .hasMessageContaining("inteiro positivo");
    }

    @Test
    void deveValidarCamposObrigatoriosEDuracaoNaoPositivaViaBeanValidationPorLinha() throws IOException {
        Path camposVazios = tempDir.resolve("campos-vazios.csv");
        Files.writeString(
                camposVazios,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + " , , , , \n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(camposVazios))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains(camposVazios.toString())
                        .contains("linha 2")
                        .contains("campo 'turma'")
                        .contains("campo 'empresa'")
                        .contains("campo 'moduloAtual'")
                        .contains("campo 'inicioModulo'")
                        .contains("campo 'duracaoSemanas'"));

        Path duracaoZero = tempDir.resolve("duracao-zero.csv");
        Files.writeString(
                duracaoZero,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02,0\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(duracaoZero))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(duracaoZero.toString())
                .hasMessageContaining("linha 2")
                .hasMessageContaining("duracaoSemanas")
                .hasMessageContaining("maior que zero");

        Path duracaoNegativa = tempDir.resolve("duracao-negativa.csv");
        Files.writeString(
                duracaoNegativa,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02,4\n"
                        + "T02,EmpresaA,Spring,2026-11-09,-2\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repository.carregar(duracaoNegativa))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(duracaoNegativa.toString())
                .hasMessageContaining("linha 3")
                .hasMessageContaining("duracaoSemanas");
    }

    @Test
    void deveRejeitarCodigoDeTurmaDuplicadoInformandoLinhasEnvolvidas() throws IOException {
        Path duplicado = tempDir.resolve("duplicado.csv");
        Files.writeString(
                duplicado,
                "turma,empresa,modulo_atual,inicio_modulo,duracao_semanas\n"
                        + "T01,EmpresaA,Java Core,2026-11-02,4\n"
                        + "R02,EmpresaB,Spring,2026-11-09,6\n"
                        + " t01 ,EmpresaA,Outro Módulo,2026-11-16,4\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.carregar(duplicado))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains(duplicado.toString())
                        .contains("linha 4")
                        .contains("duplicado")
                        .contains("t01")
                        .contains("linha 2"));
    }

    @Test
    void deveValidarParametrosNulosNoConstrutorENosMetodosPublicos() {
        assertThatNullPointerException().isThrownBy(() -> new CsvTurmaRepository(null));
        assertThatNullPointerException().isThrownBy(() -> repository.existe(null));
        assertThatNullPointerException().isThrownBy(() -> repository.carregar(null));
        assertThatNullPointerException().isThrownBy(() -> repository.carregarSeExistir(null));
    }
}
