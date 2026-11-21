package dev.cadencia.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.BlocoManual;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.dominio.TipoCompromisso;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlAgendaRepositoryTest {

    private static ValidatorFactory validatorFactory;
    private static AgendaRepository repository;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void configurarRepositorio() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        repository = new YamlAgendaRepository(yamlMapper, validator);
    }

    @AfterAll
    static void fecharValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void deveCarregarAgendaExemploCompletaSemDescartarConfiguracoes() {
        AgendaConfiguracao config = repository.carregar(Path.of("exemplos/agenda.exemplo.yaml"));

        assertThat(config.jornada().inicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(config.jornada().fim()).isEqualTo(LocalTime.of(23, 0));

        assertThat(config.margens().deslocamentoEntreLocaisMin()).isEqualTo(30);
        assertThat(config.margens().porEmpresa())
                .containsEntry("EmpresaA", new MargemEmpresa(30, 0))
                .containsEntry("EmpresaB", new MargemEmpresa(30, 15));

        assertThat(config.classificacao().profundoMinimoMin()).isEqualTo(90);
        assertThat(config.classificacao().interrompivelMinimoMin()).isEqualTo(25);

        assertThat(config.compromissos()).hasSize(18);
        Compromisso primeiraAula = config.compromissos().get(0);
        assertThat(primeiraAula.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(primeiraAula.inicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(primeiraAula.duracao()).isEqualTo(Duration.ofHours(2));
        assertThat(primeiraAula.tipo()).isEqualTo(TipoCompromisso.AULA);
        assertThat(primeiraAula.empresa()).isEqualTo("EmpresaA");
        assertThat(primeiraAula.local()).isEqualTo("UNIDADE_CENTRO");
        assertThat(primeiraAula.titulo()).isEqualTo("Turma T01");

        Compromisso reuniaoQuarta = config.compromissos().stream()
                .filter(c -> c.tipo() == TipoCompromisso.REUNIAO)
                .findFirst()
                .orElseThrow();
        assertThat(reuniaoQuarta.diaSemana()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(reuniaoQuarta.empresa()).isEqualTo("EmpresaA");

        Compromisso sabado = config.compromissos().get(17);
        assertThat(sabado.diaSemana()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(sabado.duracao()).isEqualTo(Duration.ofMinutes(150));

        assertThat(config.blocosManuais()).hasSize(2);
        BlocoManual blocoSexta = config.blocosManuais().get(0);
        assertThat(blocoSexta.diaSemana()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(blocoSexta.inicio()).isEqualTo(LocalTime.of(10, 0));
        assertThat(blocoSexta.fim()).isEqualTo(LocalTime.of(11, 30));
        assertThat(blocoSexta.tipo()).isEqualTo(TipoBloco.PROFUNDO);
        assertThat(blocoSexta.descricao()).isEqualTo("Reserva manual para estudo prático");
    }

    @Test
    void deveConverterCompromissoQueCruzaMeiaNoiteETerminoExatoNaMeiaNoite() throws IOException {
        Path arquivo = tempDir.resolve("meia-noite.yaml");
        Files.writeString(arquivo, """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 30
                  por_empresa:
                    EmpresaA:
                      antes_min: 30
                      depois_min: 0
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "23:00"
                    fim: "01:00"
                    tipo: AULA
                  - dia: TERCA
                    inicio: "23:00"
                    fim: "00:00"
                    tipo: PLANTAO
                """);

        AgendaConfiguracao config = repository.carregar(arquivo);

        assertThat(config.blocosManuais()).isEmpty();
        assertThat(config.compromissos()).hasSize(2);

        Compromisso cruza = config.compromissos().get(0);
        assertThat(cruza.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(cruza.duracao()).isEqualTo(Duration.ofHours(2));
        assertThat(cruza.cruzaMeiaNoite()).isTrue();
        assertThat(cruza.empresa()).isNull();
        assertThat(cruza.local()).isNull();
        assertThat(cruza.titulo()).isNull();

        Compromisso exato = config.compromissos().get(1);
        assertThat(exato.diaSemana()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(exato.duracao()).isEqualTo(Duration.ofHours(1));
        assertThat(exato.fim()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(exato.cruzaMeiaNoite()).isFalse();
    }

    @Test
    void deveInformarArquivoLinhaEColunaParaErroDeSintaxeYaml() throws IOException {
        Path arquivo = tempDir.resolve("sintaxe-invalida.yaml");
        Files.writeString(arquivo, """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 30
                    quebrado: [1, 2
                """);

        assertThatThrownBy(() -> repository.carregar(arquivo))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(JsonProcessingException.class)
                .hasMessageContaining(arquivo.toString())
                .hasMessageContaining("linha ")
                .hasMessageContaining("coluna ")
                .hasMessageContaining("indentação");
    }

    @Test
    void deveRejeitarArquivoYamlVazioOuNulo() throws IOException {
        Path arquivo = tempDir.resolve("vazio.yaml");
        Files.writeString(arquivo, "~\n");

        assertThatThrownBy(() -> repository.carregar(arquivo))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining(arquivo.toString())
                .hasMessageContaining("vazio ou nulo");
    }

    @Test
    void deveInformarCaminhoDoCampoSemLinhaParaHorarioInvalidoEAusenciaDeCampo() throws IOException {
        Path arquivo = tempDir.resolve("validacao-invalida.yaml");
        Files.writeString(arquivo, """
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
                    tipo: AULA
                  - dia: TERCA
                    inicio: "14:00"
                    fim: "15:00"
                """);

        assertThatThrownBy(() -> repository.carregar(arquivo))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage())
                            .contains(arquivo.toString())
                            .contains("compromissos[0].fim")
                            .contains("HH:mm entre 00:00 e 23:59")
                            .contains("compromissos[1].tipo")
                            .doesNotContain("linha")
                            .doesNotContain("coluna");
                });
    }

    @Test
    void deveRejeitarIntervaloComFimIgualAoInicioEJornadaOuBlocoManualCruzandoMeiaNoite() throws IOException {
        Path arquivo = tempDir.resolve("intervalos-invalidos.yaml");
        Files.writeString(arquivo, """
                jornada:
                  inicio: "22:00"
                  fim: "06:00"
                margens:
                  deslocamento_entre_locais_min: 30
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "10:00"
                    fim: "10:00"
                    tipo: REUNIAO
                blocos_manuais:
                  - dia: SEXTA
                    inicio: "23:00"
                    fim: "01:00"
                    tipo: PROFUNDO
                """);

        assertThatThrownBy(() -> repository.carregar(arquivo))
                .isInstanceOf(DadosInvalidosException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains("jornada.fim")
                        .contains("compromissos[0].fim")
                        .contains("blocosManuais[0].fim")
                        .doesNotContain("linha"));
    }

    @Test
    void deveInformarCaminhoEOrientacaoQuandoArquivoNaoExistir() {
        Path inexistente = tempDir.resolve("nao-existe.yaml");

        assertThatThrownBy(() -> repository.carregar(inexistente))
                .isInstanceOf(DadosInvalidosException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining(inexistente.toString())
                .hasMessageContaining("inexistente")
                .hasMessageContaining("Verifique o caminho");
    }
}
