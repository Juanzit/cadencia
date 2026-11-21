package dev.cadencia.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Jornada;
import dev.cadencia.dominio.TipoBloco;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgendaCasosDeBordaTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO_SEGUNDA_W48 = Clock.fixed(
            LocalDate.of(2026, 11, 23).atTime(8, 0).atZone(SAO_PAULO).toInstant(),
            SAO_PAULO);
    private static final Jornada JORNADA_PADRAO = new Jornada(LocalTime.of(7, 0), LocalTime.of(23, 0));

    private static ValidatorFactory validatorFactory;
    private static AgendaService service;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void configurarAmbienteEndToEnd() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        AgendaRepository repository = new YamlAgendaRepository(
                new ObjectMapper(new YAMLFactory()),
                validatorFactory.getValidator());
        service = new AgendaService(repository, RELOGIO_SEGUNDA_W48);
    }

    @AfterAll
    static void encerrarValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void deveTratarMargensSobrepostasSemBlocosDuplicadosOuDuracaoZero() throws IOException {
        Path arquivo = escreverYaml("margens-sobrepostas.yaml", """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 30
                  por_empresa:
                    EmpresaA:
                      antes_min: 30
                      depois_min: 0
                    EmpresaB:
                      antes_min: 30
                      depois_min: 15
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "08:30"
                    fim: "10:00"
                    tipo: AULA
                    empresa: EmpresaB
                    local: CASA
                    titulo: "Aula 1"
                  - dia: SEGUNDA
                    inicio: "10:30"
                    fim: "12:00"
                    tipo: AULA
                    empresa: EmpresaA
                    local: CASA
                    titulo: "Aula 2"
                """);

        AgendaExpandida expandida = service.expandir(arquivo);
        assertThat(expandida.intervalosOcupados())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 23))
                        && (i.tipo() == TipoIntervaloOcupado.MARGEM_ANTES
                                || i.tipo() == TipoIntervaloOcupado.MARGEM_DEPOIS))
                .extracting(IntervaloOcupado::tipo, IntervaloOcupado::inicio, IntervaloOcupado::fim)
                .containsExactly(
                        Tuple.tuple(TipoIntervaloOcupado.MARGEM_ANTES, LocalTime.of(8, 0), LocalTime.of(8, 30)),
                        Tuple.tuple(TipoIntervaloOcupado.MARGEM_ANTES, LocalTime.of(10, 0), LocalTime.of(10, 30)),
                        Tuple.tuple(TipoIntervaloOcupado.MARGEM_DEPOIS, LocalTime.of(10, 0), LocalTime.of(10, 15)));

        AgendaIntervalosLivres livres = service.calcularIntervalosLivres(arquivo);
        assertThat(livres.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(IntervaloLivre::inicio, IntervaloLivre::fim)
                .containsExactly(
                        Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(8, 0)),
                        Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(23, 0)));

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, JORNADA_PADRAO);
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(8, 0), TipoBloco.INTERROMPIVEL),
                        Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO));
    }

    @Test
    void deveTratarCompromissosColadosSemIntervalosOuBlocosDeDuracaoZero() throws IOException {
        Path arquivo = escreverYaml("compromissos-colados.yaml", """
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
                    inicio: "08:00"
                    fim: "09:30"
                    tipo: REUNIAO
                    local: CASA
                    titulo: "Reunião 1"
                  - dia: SEGUNDA
                    inicio: "09:30"
                    fim: "11:00"
                    tipo: REUNIAO
                    local: CASA
                    titulo: "Reunião 2"
                  - dia: SEGUNDA
                    inicio: "11:00"
                    fim: "12:00"
                    tipo: ALMOCO
                    local: CASA
                    titulo: "Almoço"
                  - dia: SEGUNDA
                    inicio: "14:00"
                    fim: "16:00"
                    tipo: AULA
                    empresa: EmpresaA
                    local: UNIDADE_CENTRO
                    titulo: "Aula centro"
                  - dia: SEGUNDA
                    inicio: "16:00"
                    fim: "16:30"
                    tipo: DESLOCAMENTO
                    local: TRANSITO
                    titulo: "Retorno"
                  - dia: SEGUNDA
                    inicio: "16:30"
                    fim: "17:30"
                    tipo: TREINO
                    local: ACADEMIA
                    titulo: "Treino"
                """);

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, JORNADA_PADRAO);

        // Entre 12:00 e 14:00 há margem antes 13:30–14:00 e deslocamento automático 13:30–14:00; sobra 12:00–13:30 (90 min)
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(8, 0), TipoBloco.INTERROMPIVEL),
                        Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(13, 30), TipoBloco.PROFUNDO),
                        Tuple.tuple(LocalTime.of(17, 30), LocalTime.of(23, 0), TipoBloco.PROFUNDO));
    }

    @Test
    void deveNaoGerarBlocosEmDiaTotalmenteOcupado() throws IOException {
        Path arquivo = escreverYaml("dia-totalmente-ocupado.yaml", """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa:
                    EmpresaB:
                      antes_min: 30
                      depois_min: 15
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "06:30"
                    fim: "13:00"
                    tipo: REUNIAO
                    titulo: "Bloco manhã"
                  - dia: SEGUNDA
                    inicio: "13:30"
                    fim: "18:00"
                    tipo: AULA
                    empresa: EmpresaB
                    titulo: "Aula tarde"
                  - dia: SEGUNDA
                    inicio: "18:15"
                    fim: "23:30"
                    tipo: TREINO
                    titulo: "Bloco noite"
                  - dia: TERCA
                    inicio: "12:00"
                    fim: "13:00"
                    tipo: ALMOCO
                    titulo: "Almoço terça"
                """);

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, JORNADA_PADRAO);

        assertThat(planejada.semana().blocos())
                .noneMatch(b -> b.data().equals(LocalDate.of(2026, 11, 23)));
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(12, 0), TipoBloco.PROFUNDO),
                        Tuple.tuple(LocalTime.of(13, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO));
    }

    @Test
    void deveGerarBlocoProfundoCobrindoTodaJornadaEmDiaVazio() throws IOException {
        Path arquivo = escreverYaml("dia-vazio.yaml", """
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
                    inicio: "10:00"
                    fim: "12:00"
                    tipo: REUNIAO
                    local: REMOTO
                    titulo: "Reunião semanal"
                """);

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, JORNADA_PADRAO);

        assertThat(planejada.avisos()).isEmpty();
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 29)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));
    }

    @Test
    void deveTratarCompromissosApos22hRecortandoLimitesDaJornada() throws IOException {
        Path arquivo = escreverYaml("compromissos-apos-22h.yaml", """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 30
                  por_empresa:
                    EmpresaB:
                      antes_min: 30
                      depois_min: 15
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "22:00"
                    fim: "23:00"
                    tipo: AULA
                    empresa: EmpresaB
                    titulo: "Aula até 23h com margem depois até 23:15"
                  - dia: TERCA
                    inicio: "22:15"
                    fim: "22:45"
                    tipo: REUNIAO
                    titulo: "Reunião noturna deixando apenas 15 min finais"
                """);

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, JORNADA_PADRAO);

        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(21, 30), TipoBloco.PROFUNDO));

        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(22, 15), TipoBloco.PROFUNDO));
    }

    @Test
    void deveFatiarCompromissoQueCruzaMeiaNoiteEOcuparFimDeUmDiaEInicioDoSeguinte() throws IOException {
        Path arquivo = escreverYaml("cruza-meia-noite.yaml", """
                jornada:
                  inicio: "00:00"
                  fim: "23:30"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "22:30"
                    fim: "01:30"
                    tipo: REUNIAO
                    titulo: "Manutenção noturna"
                  - dia: DOMINGO
                    inicio: "22:30"
                    fim: "01:30"
                    tipo: PLANTAO
                    titulo: "Plantão virada domingo"
                """);

        AgendaExpandida expandida = service.expandir(arquivo);
        assertThat(expandida.intervalosOcupados())
                .filteredOn(i -> i.descricao().contains("Manutenção noturna"))
                .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim)
                .containsExactly(
                        Tuple.tuple(LocalDate.of(2026, 11, 23), LocalTime.of(22, 30), LocalTime.MIDNIGHT),
                        Tuple.tuple(LocalDate.of(2026, 11, 24), LocalTime.MIDNIGHT, LocalTime.of(1, 30)));

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, new Jornada(LocalTime.MIDNIGHT, LocalTime.of(23, 30)));

        // Segunda 23/11: fim do dia ocupado [22:30, 23:30)
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(Tuple.tuple(LocalTime.MIDNIGHT, LocalTime.of(22, 30), TipoBloco.PROFUNDO));

        // Terça 24/11: início do dia ocupado [00:00, 01:30)
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(Tuple.tuple(LocalTime.of(1, 30), LocalTime.of(23, 30), TipoBloco.PROFUNDO));

        // Domingo 29/11: preserva fração dentro da semana/jornada e registra descarte de segunda seguinte
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 29)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        Tuple.tuple(LocalTime.MIDNIGHT, LocalTime.of(22, 30), TipoBloco.PROFUNDO),
                        Tuple.tuple(LocalTime.of(22, 30), LocalTime.of(23, 30), TipoBloco.VIGILANCIA));
        assertThat(planejada.avisos()).containsExactly(
                "Compromisso \"PLANTAO · Plantão virada domingo\" de domingo ultrapassa a semana; a fração de segunda-feira (00:00–01:30) foi descartada.");
    }

    @Test
    void devePreservarJanelaNoturnaEDeslocamentosEmSemanaComDiaSemAcademia() throws IOException {
        Path arquivo = escreverYaml("semana-dia-sem-academia.yaml", """
                jornada:
                  inicio: "07:00"
                  fim: "23:00"
                margens:
                  deslocamento_entre_locais_min: 30
                  por_empresa:
                    EmpresaB:
                      antes_min: 30
                      depois_min: 15
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos:
                  - dia: SEGUNDA
                    inicio: "16:00"
                    fim: "17:30"
                    tipo: AULA
                    empresa: EmpresaB
                    local: CASA
                    titulo: "Aula segunda"
                  - dia: SEGUNDA
                    inicio: "18:30"
                    fim: "19:30"
                    tipo: TREINO
                    local: ACADEMIA
                    titulo: "Treino segunda"
                  - dia: SEXTA
                    inicio: "12:00"
                    fim: "13:00"
                    tipo: ALMOCO
                    local: CASA
                    titulo: "Almoço sexta"
                  - dia: SEXTA
                    inicio: "15:00"
                    fim: "16:30"
                    tipo: AULA
                    empresa: EmpresaB
                    local: CASA
                    titulo: "Aula sexta sem academia"
                """);

        SemanaPlanejada planejada = service.gerarSemana(arquivo);
        validarInvariantesBlocos(planejada, JORNADA_PADRAO);

        // Segunda: deslocamento automático CASA->ACADEMIA (18:00–18:30) + treino (18:30–19:30); 17:45–18:00 descartado
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(15, 30), TipoBloco.PROFUNDO),
                        Tuple.tuple(LocalTime.of(19, 30), LocalTime.of(23, 0), TipoBloco.PROFUNDO));

        // Sexta (sem academia): noite contínua a partir de 16:45 até 23:00 sem deslocamento
        assertThat(planejada.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 27)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        Tuple.tuple(LocalTime.of(7, 0), LocalTime.of(12, 0), TipoBloco.PROFUNDO),
                        Tuple.tuple(LocalTime.of(13, 0), LocalTime.of(14, 30), TipoBloco.PROFUNDO),
                        Tuple.tuple(LocalTime.of(16, 45), LocalTime.of(23, 0), TipoBloco.PROFUNDO));
    }

    private Path escreverYaml(String nomeArquivo, String conteudoYaml) throws IOException {
        Path arquivo = tempDir.resolve(nomeArquivo);
        Files.writeString(arquivo, conteudoYaml);
        return arquivo;
    }

    private static void validarInvariantesBlocos(SemanaPlanejada planejada, Jornada jornada) {
        LocalDate inicioSemana = planejada.inicioSemana();
        LocalDate fimSemana = inicioSemana.plusDays(6);

        Map<LocalDate, List<Bloco>> porDia = planejada.semana().blocos().stream()
                .collect(Collectors.groupingBy(Bloco::data));

        for (Bloco bloco : planejada.semana().blocos()) {
            assertThat(bloco.data()).isBetween(inicioSemana, fimSemana);
            assertThat(bloco.fim()).isAfter(bloco.inicio());
            assertThat(Duration.between(bloco.inicio(), bloco.fim())).isPositive();
            assertThat(bloco.inicio()).isAfterOrEqualTo(jornada.inicio());
            assertThat(bloco.fim()).isBeforeOrEqualTo(jornada.fim());
        }

        for (List<Bloco> doDia : porDia.values()) {
            List<Bloco> ordenados = doDia.stream()
                    .sorted(Comparator.comparing(Bloco::inicio).thenComparing(Bloco::fim))
                    .toList();
            for (int i = 1; i < ordenados.size(); i++) {
                Bloco anterior = ordenados.get(i - 1);
                Bloco atual = ordenados.get(i);
                assertThat(atual.inicio()).isAfterOrEqualTo(anterior.fim());
            }
        }
    }
}
