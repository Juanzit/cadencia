package dev.cadencia.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.BlocoManual;
import dev.cadencia.dominio.Classificacao;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.Jornada;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.Margens;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.dominio.TipoCompromisso;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgendaServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO_SEGUNDA_W48 = Clock.fixed(
            LocalDate.of(2026, 11, 23).atTime(9, 0).atZone(SAO_PAULO).toInstant(),
            SAO_PAULO);
    private static final Path CAMINHO_FICTICIO = Path.of("in-memory/agenda.yaml");

    private static final Jornada JORNADA_PADRAO = new Jornada(LocalTime.of(7, 0), LocalTime.of(23, 0));
    private static final Classificacao CLASSIFICACAO_PADRAO = new Classificacao(90, 25);
    private static final Map<String, MargemEmpresa> EMPRESAS_PADRAO = Map.of(
            "EmpresaA", new MargemEmpresa(30, 0),
            "EmpresaB", new MargemEmpresa(30, 15));

    @Test
    void deveAplicarMargensSomenteParaAulasComEmpresaCadastrada() {
        List<Compromisso> compromissos = List.of(
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofHours(2),
                        TipoCompromisso.AULA, "EmpresaA", null, "Aula A"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(16, 0), Duration.ofMinutes(90),
                        TipoCompromisso.AULA, "EmpresaB", null, "Aula B"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, null, "Aula sem empresa"),
                new Compromisso(DayOfWeek.THURSDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, "EmpresaX", null, "Aula empresa não cadastrada"),
                new Compromisso(DayOfWeek.FRIDAY, LocalTime.of(11, 0), Duration.ofHours(1),
                        TipoCompromisso.REUNIAO, "EmpresaB", null, "Reunião"));

        AgendaService service = criarService(new Margens(0, EMPRESAS_PADRAO), compromissos, RELOGIO_SEGUNDA_W48);
        AgendaExpandida resultado = service.expandir(CAMINHO_FICTICIO);

        assertThat(resultado.avisos()).isEmpty();
        assertThat(resultado.intervalosOcupados())
                .filteredOn(i -> i.tipo() == TipoIntervaloOcupado.MARGEM_ANTES)
                .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(8, 30), LocalTime.of(9, 0)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 24), LocalTime.of(15, 30), LocalTime.of(16, 0)));

        assertThat(resultado.intervalosOcupados())
                .filteredOn(i -> i.tipo() == TipoIntervaloOcupado.MARGEM_DEPOIS)
                .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 24), LocalTime.of(17, 30), LocalTime.of(17, 45)));
    }

    @Test
    void deveGerarDeslocamentosAutomaticosApenasQuandoLocaisMudaremSemDeslocamentoExplicito() {
        List<Compromisso> compromissos = List.of(
                // Segunda: CASA -> UNIDADE_CENTRO sem deslocamento explícito => gera 09:30–10:00
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "CASA", "Em casa"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, "EmpresaA", "UNIDADE_CENTRO", "No centro"),
                // Terça: CASA -> "  casa  " (mesmo local) + local nulo + TRANSITO => 0 deslocamentos
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.ALMOCO, null, "CASA", "Item 1"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(9, 0), Duration.ofMinutes(15),
                        TipoCompromisso.REUNIAO, null, "   ", "Ignorar vazio"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(9, 30), Duration.ofMinutes(15),
                        TipoCompromisso.REUNIAO, null, null, "Ignorar nulo"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(10, 0), Duration.ofMinutes(30),
                        TipoCompromisso.REUNIAO, null, "transito", "Ignorar transito"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(11, 0), Duration.ofHours(1),
                        TipoCompromisso.TREINO, null, "  casa ", "Mesmo local"),
                // Quarta: CASA -> DESLOCAMENTO explícito -> UNIDADE_CENTRO => não duplica
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "CASA", "Origem"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), Duration.ofMinutes(30),
                        TipoCompromisso.DESLOCAMENTO, null, "TRANSITO", "Deslocamento explícito"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "UNIDADE_CENTRO", "Destino"));

        AgendaService service = criarService(new Margens(30, EMPRESAS_PADRAO), compromissos, RELOGIO_SEGUNDA_W48);
        AgendaExpandida resultado = service.expandir(CAMINHO_FICTICIO);

        List<IntervaloOcupado> deslocamentosAuto = resultado.intervalosOcupados().stream()
                .filter(i -> i.tipo() == TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO)
                .toList();

        assertThat(deslocamentosAuto).hasSize(1);
        assertThat(deslocamentosAuto.get(0).data()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(deslocamentosAuto.get(0).inicio()).isEqualTo(LocalTime.of(9, 30));
        assertThat(deslocamentosAuto.get(0).fim()).isEqualTo(LocalTime.of(10, 0));
        assertThat(deslocamentosAuto.get(0).duracao()).isEqualTo(Duration.ofMinutes(30));
        assertThat(deslocamentosAuto.get(0).descricao()).isEqualTo("Deslocamento automático (CASA -> UNIDADE_CENTRO)");

        // Com deslocamento configurado zero, nenhum deslocamento automático deve ser gerado
        AgendaService semDeslocamento = criarService(
                new Margens(0, EMPRESAS_PADRAO), compromissos, RELOGIO_SEGUNDA_W48);
        assertThat(semDeslocamento.expandir(CAMINHO_FICTICIO).intervalosOcupados())
                .noneMatch(i -> i.tipo() == TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO);
    }

    @Test
    void deveGerarDeslocamentoAutomaticoMesmoComDeslocamentoExplicitoAntesDoPrimeiroLocal() {
        List<Compromisso> compromissos = List.of(
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(7, 0), Duration.ofMinutes(30),
                        TipoCompromisso.DESLOCAMENTO, null, "TRANSITO", "Saída inicial"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "CASA", "Primeiro local"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "UNIDADE_CENTRO", "Segundo local"));

        AgendaService service = criarService(new Margens(30, Map.of()), compromissos, RELOGIO_SEGUNDA_W48);
        List<IntervaloOcupado> deslocamentosAuto = service.expandir(CAMINHO_FICTICIO).intervalosOcupados().stream()
                .filter(i -> i.tipo() == TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO)
                .toList();

        assertThat(deslocamentosAuto)
                .hasSize(1)
                .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim,
                        IntervaloOcupado::duracao, IntervaloOcupado::descricao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalDate.of(2026, 11, 23),
                        LocalTime.of(9, 30),
                        LocalTime.of(10, 0),
                        Duration.ofMinutes(30),
                        "Deslocamento automático (CASA -> UNIDADE_CENTRO)"));
    }

    @Test
    void deveGerarDeslocamentoAutomaticoEmTransicaoPosteriorAoDeslocamentoExplicito() {
        List<Compromisso> compromissos = List.of(
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "CASA", "Origem"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(9, 0), Duration.ofMinutes(30),
                        TipoCompromisso.DESLOCAMENTO, null, "TRANSITO", "Deslocamento explícito"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "UNIDADE_CENTRO", "Destino intermediário"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(12, 0), Duration.ofHours(1),
                        TipoCompromisso.TREINO, null, "ACADEMIA", "Destino final"));

        AgendaService service = criarService(new Margens(30, Map.of()), compromissos, RELOGIO_SEGUNDA_W48);
        List<IntervaloOcupado> deslocamentosAuto = service.expandir(CAMINHO_FICTICIO).intervalosOcupados().stream()
                .filter(i -> i.tipo() == TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO)
                .toList();

        assertThat(deslocamentosAuto)
                .hasSize(1)
                .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim,
                        IntervaloOcupado::duracao, IntervaloOcupado::descricao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalDate.of(2026, 11, 24),
                        LocalTime.of(11, 30),
                        LocalTime.of(12, 0),
                        Duration.ofMinutes(30),
                        "Deslocamento automático (UNIDADE_CENTRO -> ACADEMIA)"));
    }

    @Test
    void deveIgnorarLocaisVaziosNulosOuTransitoEPreservarLocalAnterior() {
        List<Compromisso> compromissos = List.of(
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "CASA", "Origem"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(9, 10), Duration.ofMinutes(10),
                        TipoCompromisso.REUNIAO, null, "", "Local string vazia"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(9, 30), Duration.ofMinutes(10),
                        TipoCompromisso.REUNIAO, null, null, "Local nulo"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(9, 50), Duration.ofMinutes(10),
                        TipoCompromisso.REUNIAO, null, "TRANSITO", "Local em trânsito"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, null, "UNIDADE_CENTRO", "Destino"));

        AgendaService service = criarService(new Margens(30, Map.of()), compromissos, RELOGIO_SEGUNDA_W48);
        List<IntervaloOcupado> deslocamentosAuto = service.expandir(CAMINHO_FICTICIO).intervalosOcupados().stream()
                .filter(i -> i.tipo() == TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO)
                .toList();

        assertThat(deslocamentosAuto)
                .hasSize(1)
                .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim,
                        IntervaloOcupado::duracao, IntervaloOcupado::descricao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalDate.of(2026, 11, 25),
                        LocalTime.of(10, 30),
                        LocalTime.of(11, 0),
                        Duration.ofMinutes(30),
                        "Deslocamento automático (CASA -> UNIDADE_CENTRO)"));
    }

    @Test
    void deveProduzirIntervalosEAvisosIdenticosIndependentementeDaOrdemDeEntrada() {
        Compromisso segundaMadrugada = new Compromisso(DayOfWeek.MONDAY, LocalTime.of(0, 10), Duration.ofMinutes(50),
                TipoCompromisso.AULA, "EmpresaA", null, "Aula madrugadora");
        Compromisso segundaEmpateNulo = new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                TipoCompromisso.AULA, null, null, null);
        Compromisso segundaEmpateComLocal = new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                TipoCompromisso.AULA, null, "CASA", "Com local");
        Compromisso segundaCentro = new Compromisso(DayOfWeek.MONDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                TipoCompromisso.AULA, "EmpresaA", "UNIDADE_CENTRO", "Aula centro");
        Compromisso domingoAula = new Compromisso(DayOfWeek.SUNDAY, LocalTime.of(22, 50), Duration.ofHours(1),
                TipoCompromisso.AULA, "EmpresaB", "CASA", "Aula domingo");
        Compromisso domingoPlantao = new Compromisso(DayOfWeek.SUNDAY, LocalTime.of(23, 0), Duration.ofHours(2),
                TipoCompromisso.PLANTAO, null, null, "Plantão domingo");

        List<Compromisso> listaCronologica = new ArrayList<>(List.of(
                segundaMadrugada,
                segundaEmpateNulo,
                segundaEmpateComLocal,
                segundaCentro,
                domingoAula,
                domingoPlantao));
        List<Compromisso> listaEmbaralhada = new ArrayList<>(List.of(
                domingoPlantao,
                segundaCentro,
                segundaEmpateComLocal,
                domingoAula,
                segundaEmpateNulo,
                segundaMadrugada));

        AgendaConfiguracao configCronologica = new AgendaConfiguracao(
                JORNADA_PADRAO, new Margens(30, EMPRESAS_PADRAO), CLASSIFICACAO_PADRAO, listaCronologica, List.of());
        AgendaConfiguracao configEmbaralhada = new AgendaConfiguracao(
                JORNADA_PADRAO, new Margens(30, EMPRESAS_PADRAO), CLASSIFICACAO_PADRAO, listaEmbaralhada, List.of());

        AgendaService serviceCronologico = new AgendaService(caminho -> configCronologica, RELOGIO_SEGUNDA_W48);
        AgendaService serviceEmbaralhado = new AgendaService(caminho -> configEmbaralhada, RELOGIO_SEGUNDA_W48);

        AgendaExpandida resultadoCronologico = serviceCronologico.expandir(CAMINHO_FICTICIO);
        AgendaExpandida resultadoEmbaralhado = serviceEmbaralhado.expandir(CAMINHO_FICTICIO);

        assertThat(resultadoEmbaralhado.intervalosOcupados())
                .containsExactlyElementsOf(resultadoCronologico.intervalosOcupados());
        assertThat(resultadoCronologico.avisos()).containsExactly(
                "Margem antes (EmpresaA) antecede o início da semana; a fração anterior à segunda-feira foi descartada.",
                "Margem depois (EmpresaB) ultrapassa o domingo da semana atual; a fração na semana seguinte foi descartada.",
                "Compromisso \"PLANTAO · Plantão domingo\" de domingo ultrapassa a semana; a fração de segunda-feira (00:00–01:00) foi descartada.");
        assertThat(resultadoEmbaralhado.avisos())
                .containsExactlyElementsOf(resultadoCronologico.avisos());

        assertThat(configCronologica.compromissos()).containsExactly(
                segundaMadrugada, segundaEmpateNulo, segundaEmpateComLocal, segundaCentro, domingoAula, domingoPlantao);
        assertThat(configEmbaralhada.compromissos()).containsExactly(
                domingoPlantao, segundaCentro, segundaEmpateComLocal, domingoAula, segundaEmpateNulo, segundaMadrugada);
    }

    @Test
    void deveDividirMargensNaMeiaNoiteETratarFronteirasDaSemanaComAvisos() {
        List<Compromisso> compromissos = List.of(
                // Caso segunda-feira: aula 00:10, margem anterior 30 min -> 00:00–00:10 + aviso de descarte
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(0, 10), Duration.ofMinutes(50),
                        TipoCompromisso.AULA, "EmpresaA", null, "Aula madrugadora"),
                // Margem anterior cruzando meia-noite (terça 00:10 -> segunda 23:40–00:00 + terça 00:00–00:10)
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(0, 10), Duration.ofMinutes(50),
                        TipoCompromisso.AULA, "EmpresaA", null, "Aula terça"),
                // Margem posterior cruzando meia-noite (quarta 23:50 após aula -> quarta 23:50–00:00 + quinta 00:00–00:05)
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(22, 50), Duration.ofHours(1),
                        TipoCompromisso.AULA, "EmpresaB", null, "Aula quarta"),
                // Término exato à meia-noite (quinta 23:00 + 1h) sem fragmento vazio na sexta
                new Compromisso(DayOfWeek.THURSDAY, LocalTime.of(23, 0), Duration.ofHours(1),
                        TipoCompromisso.PLANTAO, null, null, "Plantão até 00h"),
                // Caso obrigatório de domingo: aula termina domingo 23:50, margem posterior 15 min ->
                // domingo 23:50–00:00 preservado, segunda 00:00–00:05 descartada com aviso
                new Compromisso(DayOfWeek.SUNDAY, LocalTime.of(22, 50), Duration.ofHours(1),
                        TipoCompromisso.AULA, "EmpresaB", null, "Aula domingo fim 23:50"),
                // Compromisso de domingo que atravessa meia-noite -> preserva domingo, descarta segunda com aviso
                new Compromisso(DayOfWeek.SUNDAY, LocalTime.of(23, 0), Duration.ofHours(2),
                        TipoCompromisso.PLANTAO, null, null, "Plantão domingo"));

        AgendaService service = criarService(new Margens(0, EMPRESAS_PADRAO), compromissos, RELOGIO_SEGUNDA_W48);
        AgendaExpandida resultado = service.expandir(CAMINHO_FICTICIO);

        // Segunda 23/11: mantém apenas 00:00–00:10 da margem anterior (antes disso foi descartado)
        assertThat(resultado.intervalosOcupados())
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 23));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.MARGEM_ANTES);
                    assertThat(i.inicio()).isEqualTo(LocalTime.MIDNIGHT);
                    assertThat(i.fim()).isEqualTo(LocalTime.of(0, 10));
                });

        // Terça 24/11 00:10 -> gerou segunda 23:40–00:00 e terça 00:00–00:10
        assertThat(resultado.intervalosOcupados())
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 23));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.MARGEM_ANTES);
                    assertThat(i.inicio()).isEqualTo(LocalTime.of(23, 40));
                    assertThat(i.fim()).isEqualTo(LocalTime.MIDNIGHT);
                })
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 24));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.MARGEM_ANTES);
                    assertThat(i.inicio()).isEqualTo(LocalTime.MIDNIGHT);
                    assertThat(i.fim()).isEqualTo(LocalTime.of(0, 10));
                });

        // Quarta 25/11 23:50 + 15 min -> quarta 23:50–00:00 e quinta 00:00–00:05
        assertThat(resultado.intervalosOcupados())
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 25));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.MARGEM_DEPOIS);
                    assertThat(i.inicio()).isEqualTo(LocalTime.of(23, 50));
                    assertThat(i.fim()).isEqualTo(LocalTime.MIDNIGHT);
                })
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 26));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.MARGEM_DEPOIS);
                    assertThat(i.inicio()).isEqualTo(LocalTime.MIDNIGHT);
                    assertThat(i.fim()).isEqualTo(LocalTime.of(0, 5));
                });

        // Quinta 26/11 23:00–00:00: nenhum fragmento vazio na sexta 27/11
        assertThat(resultado.intervalosOcupados())
                .noneMatch(i -> i.data().equals(LocalDate.of(2026, 11, 27)));

        // Nenhum intervalo deve ultrapassar domingo 29/11
        assertThat(resultado.intervalosOcupados())
                .allMatch(i -> !i.data().isAfter(LocalDate.of(2026, 11, 29)));

        // Domingo 29/11: preserva margem 23:50–00:00 e plantão 23:00–00:00
        assertThat(resultado.intervalosOcupados())
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 29));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.MARGEM_DEPOIS);
                    assertThat(i.inicio()).isEqualTo(LocalTime.of(23, 50));
                    assertThat(i.fim()).isEqualTo(LocalTime.MIDNIGHT);
                })
                .anySatisfy(i -> {
                    assertThat(i.data()).isEqualTo(LocalDate.of(2026, 11, 29));
                    assertThat(i.tipo()).isEqualTo(TipoIntervaloOcupado.COMPROMISSO);
                    assertThat(i.inicio()).isEqualTo(LocalTime.of(23, 0));
                    assertThat(i.fim()).isEqualTo(LocalTime.MIDNIGHT);
                });

        // Avisos explícitos gerados para segunda anterior, margem de domingo e compromisso de domingo
        assertThat(resultado.avisos()).hasSize(3);
        assertThat(resultado.avisos())
                .anyMatch(a -> a.contains("anterior à segunda-feira"))
                .anyMatch(a -> a.contains("Margem depois (EmpresaB)") && a.contains("domingo"))
                .anyMatch(a -> a.contains("Plantão domingo") && a.contains("segunda-feira"));
    }

    @Test
    void deveSempreUsarFusoSaoPauloIndependenteDoFusoDoClock() {
        // 2026-11-23T01:30Z ainda é domingo 2026-11-22 22:30 em America/Sao_Paulo (semana inicia em 16/11)
        Clock relogioUtc = Clock.fixed(Instant.parse("2026-11-23T01:30:00Z"), ZoneOffset.UTC);
        AgendaService service = criarService(new Margens(0, Map.of()), List.of(), relogioUtc);

        AgendaExpandida expandida = service.expandir(CAMINHO_FICTICIO);
        AgendaIntervalosLivres livres = service.calcularIntervalosLivres(CAMINHO_FICTICIO);

        assertThat(expandida.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(livres.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(livres.intervalosLivres().get(0).data()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void deveExpandirAgendaExemploRealComDeterminismoEImutabilidade() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository yamlRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()),
                    factory.getValidator());
            AgendaService service = new AgendaService(yamlRepo, RELOGIO_SEGUNDA_W48);

            AgendaExpandida primeiraExecucao = service.expandir(Path.of("exemplos/agenda.exemplo.yaml"));
            AgendaExpandida segundaExecucao = service.expandir(Path.of("exemplos/agenda.exemplo.yaml"));

            assertThat(primeiraExecucao).isEqualTo(segundaExecucao);
            assertThat(primeiraExecucao.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 23));
            assertThat(primeiraExecucao.configuracao().blocosManuais()).hasSize(2);
            assertThat(primeiraExecucao.avisos()).isEmpty();

            List<IntervaloOcupado> intervalos = primeiraExecucao.intervalosOcupados();
            assertThat(intervalos)
                    .filteredOn(i -> i.tipo() == TipoIntervaloOcupado.COMPROMISSO)
                    .hasSize(18);
            assertThat(intervalos)
                    .filteredOn(i -> i.tipo() == TipoIntervaloOcupado.MARGEM_ANTES)
                    .hasSize(6);
            assertThat(intervalos)
                    .filteredOn(i -> i.tipo() == TipoIntervaloOcupado.MARGEM_DEPOIS)
                    .hasSize(3);
            assertThat(intervalos)
                    .filteredOn(i -> i.tipo() == TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO)
                    .extracting(IntervaloOcupado::data, IntervaloOcupado::inicio, IntervaloOcupado::fim)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 23), LocalTime.of(18, 0), LocalTime.of(18, 30)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 25), LocalTime.of(10, 30), LocalTime.of(11, 0)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 25), LocalTime.of(12, 0), LocalTime.of(12, 30)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 26), LocalTime.of(13, 30), LocalTime.of(14, 0)));

            assertThatThrownBy(() -> intervalos.clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> primeiraExecucao.avisos().add("novo"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void intervaloOcupadoEAgendaExpandidaDevemValidarInvariantes() {
        LocalDate data = LocalDate.of(2026, 11, 23);
        assertThatNullPointerException()
                .isThrownBy(() -> new IntervaloOcupado(
                        null, LocalTime.of(9, 0), Duration.ofMinutes(30), TipoIntervaloOcupado.COMPROMISSO, "x"));
        assertThatThrownBy(() -> new IntervaloOcupado(
                        data, LocalTime.of(9, 0), Duration.ZERO, TipoIntervaloOcupado.COMPROMISSO, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntervaloOcupado(
                        data, LocalTime.of(23, 30), Duration.ofHours(1), TipoIntervaloOcupado.COMPROMISSO, "x"))
                .isInstanceOf(IllegalArgumentException.class);

        AgendaConfiguracao config = new AgendaConfiguracao(
                JORNADA_PADRAO, new Margens(0, Map.of()), CLASSIFICACAO_PADRAO, List.of(), List.of());
        List<IntervaloOcupado> mutavel = new ArrayList<>();
        List<String> avisosMutaveis = new ArrayList<>();
        AgendaExpandida expandida = new AgendaExpandida(data, config, mutavel, avisosMutaveis);
        mutavel.add(new IntervaloOcupado(
                data, LocalTime.of(9, 0), Duration.ofMinutes(30), TipoIntervaloOcupado.COMPROMISSO, "x"));
        avisosMutaveis.add("aviso");
        assertThat(expandida.intervalosOcupados()).isEmpty();
        assertThat(expandida.avisos()).isEmpty();
    }

    @Test
    void deveGerarUmIntervaloPorDiaParaSemanaOuDomingoTotalmenteLivres() {
        AgendaService semCompromissos = criarService(new Margens(0, Map.of()), List.of(), RELOGIO_SEGUNDA_W48);
        AgendaIntervalosLivres resultado = semCompromissos.calcularIntervalosLivres(CAMINHO_FICTICIO);

        assertThat(resultado.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(resultado.avisos()).isEmpty();
        assertThat(resultado.intervalosLivres())
                .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 24), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 25), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 26), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 27), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 28), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 29), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)));
    }

    @Test
    void naoDeveGerarIntervalosLivresEmDiaTotalmenteOcupado() {
        List<Compromisso> compromissos = List.of(
                // Segunda: ocupação exata 07:00–23:00
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(7, 0), Duration.ofHours(16),
                        TipoCompromisso.PLANTAO, null, null, "Plantão dia inteiro"),
                // Terça: soma contínua excedendo a janela (06:00–15:00 + 15:00–00:00)
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(6, 0), Duration.ofHours(9),
                        TipoCompromisso.PLANTAO, null, null, "Manhã/tarde"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(15, 0), Duration.ofHours(9),
                        TipoCompromisso.PLANTAO, null, null, "Tarde/noite até 00h"));

        AgendaService service = criarService(new Margens(0, Map.of()), compromissos, RELOGIO_SEGUNDA_W48);
        AgendaIntervalosLivres resultado = service.calcularIntervalosLivres(CAMINHO_FICTICIO);

        assertThat(resultado.intervalosLivres())
                .noneMatch(i -> i.data().equals(LocalDate.of(2026, 11, 23))
                        || i.data().equals(LocalDate.of(2026, 11, 24)));
        assertThat(resultado.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 29)))
                .extracting(IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)));
    }

    @Test
    void deveMesclarSobreposicoesEAdjacenciasComMargensEDeslocamento() {
        List<Compromisso> compromissos = List.of(
                // 08:00–09:00 CASA
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.REUNIAO, null, "CASA", "Reunião inicial"),
                // 09:30–11:00 AULA EmpresaB UNIDADE_CENTRO => MARGEM_ANTES 09:00–09:30,
                // DESLOCAMENTO_AUTOMATICO 09:00–09:30 (sobreposto) e MARGEM_DEPOIS 11:00–11:15
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(9, 30), Duration.ofMinutes(90),
                        TipoCompromisso.AULA, "EmpresaB", "UNIDADE_CENTRO", "Aula centro"),
                // 10:45–12:00 sobrepõe aula e MARGEM_DEPOIS
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(10, 45), Duration.ofMinutes(75),
                        TipoCompromisso.REUNIAO, null, "UNIDADE_CENTRO", "Reunião sobreposta"),
                // 12:00–13:00 adjacente a 12:00 => bloco ocupado contínuo [08:00, 13:00)
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(12, 0), Duration.ofHours(1),
                        TipoCompromisso.ALMOCO, null, "UNIDADE_CENTRO", "Almoço"));

        AgendaService service = criarService(new Margens(30, EMPRESAS_PADRAO), compromissos, RELOGIO_SEGUNDA_W48);
        List<IntervaloLivre> livresSegunda = service.calcularIntervalosLivres(CAMINHO_FICTICIO)
                .intervalosLivres().stream()
                .filter(i -> i.data().equals(LocalDate.of(2026, 11, 23)))
                .toList();

        assertThat(livresSegunda)
                .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(7, 0), LocalTime.of(8, 0), Duration.ofHours(1)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(13, 0), LocalTime.of(23, 0), Duration.ofHours(10)));
    }

    @Test
    void deveRecortarOcupacoesAntesDoInicioEDepoisDoFimDaJornadaIncluindoMeiaNoite() {
        List<Compromisso> compromissos = List.of(
                // Segunda (jornada 07:00–23:00):
                // 05:00–06:30 fora da jornada; aula 07:10–08:00 gera margem antes 06:40–07:10 (recortada em 07:00)
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(5, 0), Duration.ofMinutes(90),
                        TipoCompromisso.TREINO, null, null, "Antes da jornada"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(7, 10), Duration.ofMinutes(50),
                        TipoCompromisso.AULA, "EmpresaA", null, "Aula cedo"),
                // Segunda: 22:00 + 2h = 22:00–00:00 (término MIDNIGHT -> 86_400s em nanos -> recortado para 22:00–23:00)
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(22, 0), Duration.ofHours(2),
                        TipoCompromisso.PLANTAO, null, null, "Plantão até 00h"),
                // Terça: 23:00–00:00 está totalmente após a jornada 07:00–23:00 -> ignorado
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(23, 0), Duration.ofHours(1),
                        TipoCompromisso.PLANTAO, null, null, "Após a jornada"),
                // Quarta: aula 22:00–22:50 (margem depois 22:50–23:05) recortada pelo fim da jornada 23:00
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(22, 0), Duration.ofMinutes(50),
                        TipoCompromisso.AULA, "EmpresaB", null, "Aula noturna"));

        AgendaService service = criarService(new Margens(0, EMPRESAS_PADRAO), compromissos, RELOGIO_SEGUNDA_W48);
        AgendaIntervalosLivres resultado = service.calcularIntervalosLivres(CAMINHO_FICTICIO);

        // Segunda: [08:00, 22:00) -> 14h
        assertThat(resultado.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalTime.of(8, 0), LocalTime.of(22, 0), Duration.ofHours(14)));

        // Terça: [07:00, 23:00) -> 16h (ocupação 23:00–00:00 não intersecta)
        assertThat(resultado.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)));

        // Quarta: [07:00, 21:30) -> 14h30 (margem antes 21:30–22:00; aula+margem depois ultrapassam 23:00)
        assertThat(resultado.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 25)))
                .extracting(IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalTime.of(7, 0), LocalTime.of(21, 30), Duration.ofMinutes(870)));

        // Jornada até 23:30 também deve tratar ocupação 23:00–00:00 com fim MIDNIGHT sem perder recorte 23:00–23:30
        AgendaService ate2330 = criarService(
                new Jornada(LocalTime.of(7, 0), LocalTime.of(23, 30)),
                new Margens(0, Map.of()),
                List.of(new Compromisso(DayOfWeek.MONDAY, LocalTime.of(23, 0), Duration.ofHours(1),
                        TipoCompromisso.PLANTAO, null, null, "Até 00h")),
                RELOGIO_SEGUNDA_W48);
        assertThat(ate2330.calcularIntervalosLivres(CAMINHO_FICTICIO).intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)));
    }

    @Test
    void deveGerarMultiplosIntervalosLivresOrdenadosERespeitarFronteirasSemDuracaoZero() {
        List<Compromisso> compromissos = List.of(
                // Segunda: múltiplas ocupações fora de ordem de entrada
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(18, 0), Duration.ofMinutes(135),
                        TipoCompromisso.AULA, null, null, "Noite"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(1),
                        TipoCompromisso.TREINO, null, null, "Manhã"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(11, 30), Duration.ofMinutes(90),
                        TipoCompromisso.ALMOCO, null, null, "Almoço"),
                // Terça: fronteiras exatas da jornada [07:00, 23:00) e blocos colados em 11:00
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(6, 0), Duration.ofHours(1),
                        TipoCompromisso.TREINO, null, null, "Termina em 07:00"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(7, 0), Duration.ofHours(1),
                        TipoCompromisso.REUNIAO, null, null, "Inicia em 07:00"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(10, 0), Duration.ofHours(1),
                        TipoCompromisso.REUNIAO, null, null, "Colado 1"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(11, 0), Duration.ofHours(1),
                        TipoCompromisso.REUNIAO, null, null, "Colado 2"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(22, 0), Duration.ofHours(1),
                        TipoCompromisso.PLANTAO, null, null, "Termina em 23:00"),
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(23, 0), Duration.ofHours(1),
                        TipoCompromisso.PLANTAO, null, null, "Inicia em 23:00"));

        AgendaService service = criarService(new Margens(0, Map.of()), compromissos, RELOGIO_SEGUNDA_W48);
        AgendaIntervalosLivres resultado = service.calcularIntervalosLivres(CAMINHO_FICTICIO);

        assertThat(resultado.intervalosLivres())
                .allSatisfy(i -> {
                    assertThat(i.fim()).isAfter(i.inicio());
                    assertThat(i.duracao()).isPositive();
                });

        assertThat(resultado.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(7, 0), LocalTime.of(8, 0), Duration.ofHours(1)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(9, 0), LocalTime.of(11, 30), Duration.ofMinutes(150)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(13, 0), LocalTime.of(18, 0), Duration.ofHours(5)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(20, 15), LocalTime.of(23, 0), Duration.ofMinutes(165)));

        assertThat(resultado.intervalosLivres())
                .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 24), LocalTime.of(8, 0), LocalTime.of(10, 0), Duration.ofHours(2)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 24), LocalTime.of(12, 0), LocalTime.of(22, 0), Duration.ofHours(10)));
    }

    @Test
    void deveCalcularIntervalosLivresExatosDaAgendaExemploPreservandoBlocosManuaisEImutabilidade() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository yamlRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()),
                    factory.getValidator());
            AgendaService service = new AgendaService(yamlRepo, RELOGIO_SEGUNDA_W48);

            AgendaIntervalosLivres primeiraExecucao = service.calcularIntervalosLivres(
                    Path.of("exemplos/agenda.exemplo.yaml"));
            AgendaIntervalosLivres segundaExecucao = service.calcularIntervalosLivres(
                    Path.of("exemplos/agenda.exemplo.yaml"));

            assertThat(primeiraExecucao).isEqualTo(segundaExecucao);
            assertThat(primeiraExecucao.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 23));
            assertThat(primeiraExecucao.configuracao().blocosManuais()).hasSize(2);
            assertThat(primeiraExecucao.avisos()).isEmpty();

            // Segunda 23/11
            assertThat(primeiraExecucao.intervalosLivres())
                    .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 23)))
                    .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 23), LocalTime.of(7, 0), LocalTime.of(8, 30), Duration.ofMinutes(90)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 23), LocalTime.of(11, 30), LocalTime.of(12, 0), Duration.ofMinutes(30)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 23), LocalTime.of(13, 0), LocalTime.of(15, 30), Duration.ofMinutes(150)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 23), LocalTime.of(17, 45), LocalTime.of(18, 0), Duration.ofMinutes(15)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 23), LocalTime.of(19, 30), LocalTime.of(23, 0), Duration.ofMinutes(210)));

            // Quarta 25/11: confirma que PLANTAO 15:00–17:00 continua ocupado e bloco manual não vira livre
            assertThat(primeiraExecucao.intervalosLivres())
                    .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 25)))
                    .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 25), LocalTime.of(7, 0), LocalTime.of(8, 0), Duration.ofHours(1)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 25), LocalTime.of(9, 15), LocalTime.of(10, 30), Duration.ofMinutes(75)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 25), LocalTime.of(13, 30), LocalTime.of(15, 0), Duration.ofMinutes(90)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 25), LocalTime.of(17, 0), LocalTime.of(23, 0), Duration.ofHours(6)));

            // Sexta 27/11: bloco manual 10:00–11:30 não divide a janela livre 07:00–12:00
            assertThat(primeiraExecucao.intervalosLivres())
                    .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 27)))
                    .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 27), LocalTime.of(7, 0), LocalTime.of(12, 0), Duration.ofHours(5)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 27), LocalTime.of(13, 0), LocalTime.of(14, 30), Duration.ofMinutes(90)),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 27), LocalTime.of(16, 45), LocalTime.of(23, 0), Duration.ofMinutes(375)));

            // Domingo 29/11: dia inteiro livre
            assertThat(primeiraExecucao.intervalosLivres())
                    .filteredOn(i -> i.data().equals(LocalDate.of(2026, 11, 29)))
                    .extracting(IntervaloLivre::data, IntervaloLivre::inicio, IntervaloLivre::fim, IntervaloLivre::duracao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalDate.of(2026, 11, 29), LocalTime.of(7, 0), LocalTime.of(23, 0), Duration.ofHours(16)));

            assertThatThrownBy(() -> primeiraExecucao.intervalosLivres().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> primeiraExecucao.avisos().add("novo"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void intervaloLivreEAgendaIntervalosLivresDevemValidarInvariantesEImutabilidade() {
        LocalDate data = LocalDate.of(2026, 11, 23);
        assertThatNullPointerException()
                .isThrownBy(() -> new IntervaloLivre(null, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        assertThatNullPointerException()
                .isThrownBy(() -> new IntervaloLivre(data, null, LocalTime.of(10, 0)));
        assertThatNullPointerException()
                .isThrownBy(() -> new IntervaloLivre(data, LocalTime.of(9, 0), null));
        assertThatThrownBy(() -> new IntervaloLivre(data, LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntervaloLivre(data, LocalTime.of(10, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        IntervaloLivre livre = new IntervaloLivre(data, LocalTime.of(8, 30), LocalTime.of(10, 0));
        assertThat(livre.duracao()).isEqualTo(Duration.ofMinutes(90));

        AgendaConfiguracao config = new AgendaConfiguracao(
                JORNADA_PADRAO, new Margens(0, Map.of()), CLASSIFICACAO_PADRAO, List.of(), List.of());
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaIntervalosLivres(null, config, List.of(), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaIntervalosLivres(data, null, List.of(), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaIntervalosLivres(data, config, null, List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgendaIntervalosLivres(data, config, List.of(), null));

        List<IntervaloLivre> livresMutaveis = new ArrayList<>();
        List<String> avisosMutaveis = new ArrayList<>();
        AgendaIntervalosLivres container = new AgendaIntervalosLivres(data, config, livresMutaveis, avisosMutaveis);
        livresMutaveis.add(livre);
        avisosMutaveis.add("aviso");
        assertThat(container.intervalosLivres()).isEmpty();
        assertThat(container.avisos()).isEmpty();
        assertThatThrownBy(() -> container.intervalosLivres().add(livre))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> container.avisos().add("novo"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveClassificarBlocosNosLimitesExatosUsandoConfiguracaoClassificacaoEDescartarMenores() {
        // Segunda com janelas exatas:
        // 07:00–07:24 (24 min -> descartado) | 07:24–08:00 ocupado
        // 08:00–08:25 (25 min -> INTERROMPIVEL) | 08:25–09:00 ocupado
        // 09:00–10:29 (89 min -> INTERROMPIVEL) | 10:29–11:00 ocupado
        // 11:00–12:30 (90 min -> PROFUNDO) | 12:30–23:00 ocupado
        List<Compromisso> compromissos = List.of(
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(7, 24), Duration.ofMinutes(36),
                        TipoCompromisso.REUNIAO, null, null, "Bloqueio 1"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(8, 25), Duration.ofMinutes(35),
                        TipoCompromisso.REUNIAO, null, null, "Bloqueio 2"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(10, 29), Duration.ofMinutes(31),
                        TipoCompromisso.REUNIAO, null, null, "Bloqueio 3"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(12, 30), Duration.ofMinutes(630),
                        TipoCompromisso.REUNIAO, null, null, "Bloqueio 4"));

        AgendaService padrao = criarService(
                JORNADA_PADRAO, new Margens(0, Map.of()), CLASSIFICACAO_PADRAO, compromissos, List.of(), RELOGIO_SEGUNDA_W48);
        SemanaPlanejada resultadoPadrao = padrao.gerarSemana(CAMINHO_FICTICIO);

        assertThat(resultadoPadrao.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::data, Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(8, 0), LocalTime.of(8, 25),
                                TipoBloco.INTERROMPIVEL, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(9, 0), LocalTime.of(10, 29),
                                TipoBloco.INTERROMPIVEL, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(11, 0), LocalTime.of(12, 30),
                                TipoBloco.PROFUNDO, null));

        // Limites customizados (60 / 15 min) comprovando uso exclusivo de configuracao.classificacao()
        AgendaService custom = criarService(
                new Jornada(LocalTime.of(7, 0), LocalTime.of(10, 0)),
                new Margens(0, Map.of()),
                new Classificacao(60, 15),
                List.of(
                        new Compromisso(DayOfWeek.MONDAY, LocalTime.of(7, 14), Duration.ofMinutes(46),
                                TipoCompromisso.REUNIAO, null, null, "Deixa 14m e 60m"),
                        new Compromisso(DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofMinutes(45),
                                TipoCompromisso.REUNIAO, null, null, "Deixa 15m")),
                List.of(),
                RELOGIO_SEGUNDA_W48);

        assertThat(custom.gerarSemana(CAMINHO_FICTICIO).semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(9, 45), LocalTime.of(10, 0), TipoBloco.INTERROMPIVEL));
    }

    @Test
    void deveConverterPlantaoParaVigilanciaRespeitandoMeiaNoiteLimitesSemanaisETipoEnum() {
        List<Compromisso> compromissos = List.of(
                // Segunda 14:00–16:00 REUNIAO cujo título contém "Plantão" NÃO deve virar VIGILANCIA
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(14, 0), Duration.ofHours(2),
                        TipoCompromisso.REUNIAO, "EmpresaA", "CASA", "Plantão pedagógico de dúvidas"),
                // Segunda 22:30 + 3h PLANTAO cruza meia-noite -> Segunda 22:30–23:30 e Terça 01:00–01:30 na jornada 01:00–23:30
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(22, 30), Duration.ofHours(3),
                        TipoCompromisso.PLANTAO, null, "CASA", "Atendimento noturno"),
                // Domingo 22:30 + 3h PLANTAO -> Domingo 22:30–23:30 preservado e fração de segunda descartada com aviso
                new Compromisso(DayOfWeek.SUNDAY, LocalTime.of(22, 30), Duration.ofHours(3),
                        TipoCompromisso.PLANTAO, null, null, "Plantão domingo"));

        AgendaService service = criarService(
                new Jornada(LocalTime.of(1, 0), LocalTime.of(23, 30)),
                new Margens(0, Map.of()),
                CLASSIFICACAO_PADRAO,
                compromissos,
                List.of(),
                RELOGIO_SEGUNDA_W48);
        SemanaPlanejada resultado = service.gerarSemana(CAMINHO_FICTICIO);

        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.tipo() == TipoBloco.VIGILANCIA)
                .extracting(Bloco::data, Bloco::inicio, Bloco::fim, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 23), LocalTime.of(22, 30), LocalTime.of(23, 30),
                                "PLANTAO · CASA · Atendimento noturno"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 24), LocalTime.of(1, 0), LocalTime.of(1, 30),
                                "PLANTAO · CASA · Atendimento noturno"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 11, 29), LocalTime.of(22, 30), LocalTime.of(23, 30),
                                "PLANTAO · Plantão domingo"));

        assertThat(resultado.avisos()).containsExactly(
                "Compromisso \"PLANTAO · Plantão domingo\" de domingo ultrapassa a semana; a fração de segunda-feira (00:00–01:30) foi descartada.");
    }

    @Test
    void deveAplicarPrecedenciaManualTotalEParcialSobrePlantaoSemDescartarReuniaoCoincidente() {
        List<Compromisso> compromissos = List.of(
                // Segunda: substituição total de PLANTAO 14:00–17:00
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(14, 0), Duration.ofHours(3),
                        TipoCompromisso.PLANTAO, null, null, "Plantão segunda"),
                // Terça: substituição parcial no meio de PLANTAO 14:00–18:00 (preserva 14:00–15:00 e 16:30–18:00)
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(14, 0), Duration.ofHours(4),
                        TipoCompromisso.PLANTAO, "EmpresaB", "CASA", "Plantão terça"),
                // Quarta: PLANTAO 14:00–18:00 coincide com REUNIAO 15:00–16:00; BlocoManual 14:30–16:30
                // NÃO pode sobrescrever a reunião (15:00–16:00 continua protegido)
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), Duration.ofHours(4),
                        TipoCompromisso.PLANTAO, null, null, "Plantão quarta"),
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0), Duration.ofHours(1),
                        TipoCompromisso.REUNIAO, null, null, "Reunião coincidente"));

        List<BlocoManual> manuais = List.of(
                new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(17, 0),
                        TipoBloco.PROFUNDO, "Estudo no plantão total"),
                new BlocoManual(DayOfWeek.TUESDAY, LocalTime.of(15, 0), LocalTime.of(16, 30),
                        TipoBloco.INTERROMPIVEL, "Revisão parcial"),
                new BlocoManual(DayOfWeek.WEDNESDAY, LocalTime.of(14, 30), LocalTime.of(16, 30),
                        TipoBloco.PROFUNDO, "Estudo sobre reunião"));

        AgendaService service = criarService(
                JORNADA_PADRAO, new Margens(0, Map.of()), CLASSIFICACAO_PADRAO, compromissos, manuais, RELOGIO_SEGUNDA_W48);
        SemanaPlanejada resultado = service.gerarSemana(CAMINHO_FICTICIO);

        // Segunda 23/11: 07:00–14:00 PROFUNDO, 14:00–17:00 PROFUNDO manual (sem VIGILANCIA duplicado), 17:00–23:00 PROFUNDO
        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(7, 0), LocalTime.of(14, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(14, 0), LocalTime.of(17, 0), TipoBloco.PROFUNDO, "Estudo no plantão total"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(17, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

        // Terça 24/11: preserva as duas partes restantes do PLANTAO como VIGILANCIA
        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(7, 0), LocalTime.of(14, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(14, 0), LocalTime.of(15, 0), TipoBloco.VIGILANCIA, "PLANTAO · EmpresaB · CASA · Plantão terça"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(15, 0), LocalTime.of(16, 30), TipoBloco.INTERROMPIVEL, "Revisão parcial"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(16, 30), LocalTime.of(18, 0), TipoBloco.VIGILANCIA, "PLANTAO · EmpresaB · CASA · Plantão terça"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(18, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

        // Quarta 25/11: reunião 15:00–16:00 protegida (nem manual nem VIGILANCIA)
        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 25)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(7, 0), LocalTime.of(14, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(14, 0), LocalTime.of(14, 30), TipoBloco.VIGILANCIA, "PLANTAO · Plantão quarta"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(14, 30), LocalTime.of(15, 0), TipoBloco.PROFUNDO, "Estudo sobre reunião"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(16, 0), LocalTime.of(16, 30), TipoBloco.PROFUNDO, "Estudo sobre reunião"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(16, 30), LocalTime.of(18, 0), TipoBloco.VIGILANCIA, "PLANTAO · Plantão quarta"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(18, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

        assertThat(resultado.avisos())
                .containsExactly(
                        "Bloco manual \"Estudo sobre reunião\" (PROFUNDO) em 2026-11-25 (14:30–16:30) foi recortado por conflito com ocupação não-plantão ou limites da jornada.");
    }

    @Test
    void deveRecortarIntervaloLivrePorBlocoManualPreservandoTipoManualETratarConflitosEDescartes() {
        List<Compromisso> compromissos = List.of(
                // Segunda: AULA 09:00–10:00 com margem antes 30m (08:30–10:00 ocupado) + REUNIAO 14:00–16:00
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(9, 0), Duration.ofHours(1),
                        TipoCompromisso.AULA, "EmpresaA", null, "Aula manhã"),
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(14, 0), Duration.ofHours(2),
                        TipoCompromisso.REUNIAO, null, null, "Reunião tarde"),
                // Sexta: almoço 12:00–13:00, bloco manual 10:00–11:30 corta 07:00–12:00 em 07:00–10:00 PROFUNDO + 10:00–11:30 manual + 11:30–12:00 INTERROMPIVEL
                new Compromisso(DayOfWeek.FRIDAY, LocalTime.of(12, 0), Duration.ofHours(1),
                        TipoCompromisso.ALMOCO, null, null, "Almoço"));

        List<BlocoManual> manuais = List.of(
                // Segunda: sobrepõe parcialmente margem/aula (08:00–11:00 -> mantém 08:00–08:30 e 10:00–11:00)
                new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(11, 0),
                        TipoBloco.PROFUNDO, "Conflito parcial"),
                // Segunda: totalmente descartado (dentro da reunião 14:00–15:30) e outro fora da jornada (05:00–06:30)
                new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 30),
                        TipoBloco.PROFUNDO, "Totalmente ocupado"),
                new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(5, 0), LocalTime.of(6, 30),
                        TipoBloco.INTERROMPIVEL, "Fora da jornada"),
                // Terça: bloco manual curto (20 min) preservado com tipo PROFUNDO e longo (2h) preservado com INTERROMPIVEL
                new BlocoManual(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(9, 20),
                        TipoBloco.PROFUNDO, "Manual curto"),
                new BlocoManual(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(16, 0),
                        TipoBloco.INTERROMPIVEL, "Manual longo"),
                // Sexta: recorta 07:00–12:00
                new BlocoManual(DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 30),
                        TipoBloco.PROFUNDO, "Reserva manual"));

        AgendaService service = criarService(
                JORNADA_PADRAO, new Margens(0, EMPRESAS_PADRAO), CLASSIFICACAO_PADRAO, compromissos, manuais, RELOGIO_SEGUNDA_W48);
        SemanaPlanejada resultado = service.gerarSemana(CAMINHO_FICTICIO);

        // Segunda 23/11: 07:00–08:00 INTERROMPIVEL | 08:00–08:30 PROFUNDO manual | 10:00–11:00 PROFUNDO manual | 11:00–14:00 PROFUNDO | 16:00–23:00 PROFUNDO
        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(7, 0), LocalTime.of(8, 0), TipoBloco.INTERROMPIVEL, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(8, 0), LocalTime.of(8, 30), TipoBloco.PROFUNDO, "Conflito parcial"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(10, 0), LocalTime.of(11, 0), TipoBloco.PROFUNDO, "Conflito parcial"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(11, 0), LocalTime.of(14, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(16, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

        // Terça 24/11: tipos manuais preservados sem reclassificação por duração
        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 24)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(7, 0), LocalTime.of(9, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(9, 0), LocalTime.of(9, 20), TipoBloco.PROFUNDO, "Manual curto"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(9, 20), LocalTime.of(14, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(14, 0), LocalTime.of(16, 0), TipoBloco.INTERROMPIVEL, "Manual longo"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(16, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

        // Sexta 27/11: 07:00–10:00 PROFUNDO | 10:00–11:30 PROFUNDO manual | 11:30–12:00 INTERROMPIVEL | 13:00–23:00 PROFUNDO
        assertThat(resultado.semana().blocos())
                .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 27)))
                .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(7, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(10, 0), LocalTime.of(11, 30), TipoBloco.PROFUNDO, "Reserva manual"),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(11, 30), LocalTime.of(12, 0), TipoBloco.INTERROMPIVEL, null),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalTime.of(13, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

        assertThat(resultado.avisos()).containsExactly(
                "Bloco manual \"Fora da jornada\" (INTERROMPIVEL) em 2026-11-23 (05:00–06:30) foi descartado por conflito com ocupação não-plantão ou por estar fora da jornada.",
                "Bloco manual \"Conflito parcial\" (PROFUNDO) em 2026-11-23 (08:00–11:00) foi recortado por conflito com ocupação não-plantão ou limites da jornada.",
                "Bloco manual \"Totalmente ocupado\" (PROFUNDO) em 2026-11-23 (14:00–15:30) foi descartado por conflito com ocupação não-plantão ou por estar fora da jornada.");
    }

    @Test
    void deveCarregarAgendaUmaUnicaVezRejeitarBlocosManuaisSobrepostosECalcularAnoIsoNaViradaDeAno() {
        AtomicInteger leituras = new AtomicInteger();
        AgendaConfiguracao configComValidacao = new AgendaConfiguracao(
                JORNADA_PADRAO,
                new Margens(0, Map.of()),
                CLASSIFICACAO_PADRAO,
                List.of(),
                List.of());
        AgendaRepository repoContado = caminho -> {
            leituras.incrementAndGet();
            return configComValidacao;
        };

        // Virada 2025 -> 2026: 2025-12-30 (terça) pertence à semana ISO 2026-W01 (segunda 2025-12-29)
        Clock relogioVirada2026 = Clock.fixed(
                LocalDate.of(2025, 12, 30).atTime(10, 0).atZone(SAO_PAULO).toInstant(),
                SAO_PAULO);
        SemanaPlanejada virada2026 = new AgendaService(repoContado, relogioVirada2026).gerarSemana(CAMINHO_FICTICIO);

        assertThat(leituras.get()).isEqualTo(1);
        assertThat(virada2026.inicioSemana()).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(virada2026.semana().ano()).isEqualTo(2026);
        assertThat(virada2026.semana().numeroIso()).isEqualTo(1);

        // Virada 2020 -> 2021: 2021-01-01 (sexta) pertence à semana ISO 2020-W53 (segunda 2020-12-28)
        Clock relogioVirada2020 = Clock.fixed(
                LocalDate.of(2021, 1, 1).atTime(10, 0).atZone(SAO_PAULO).toInstant(),
                SAO_PAULO);
        SemanaPlanejada virada2020 = new AgendaService(repoContado, relogioVirada2020).gerarSemana(CAMINHO_FICTICIO);
        assertThat(virada2020.inicioSemana()).isEqualTo(LocalDate.of(2020, 12, 28));
        assertThat(virada2020.semana().ano()).isEqualTo(2020);
        assertThat(virada2020.semana().numeroIso()).isEqualTo(53);

        // Dois BlocoManual sobrepostos no mesmo dia mesmo via repositório em memória devem falhar explicitamente
        AgendaService comManuaisSobrepostos = criarService(
                JORNADA_PADRAO,
                new Margens(0, Map.of()),
                CLASSIFICACAO_PADRAO,
                List.of(),
                List.of(
                        new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0),
                                TipoBloco.PROFUNDO, null),
                        new BlocoManual(DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(12, 0),
                                TipoBloco.INTERROMPIVEL, "Sobreposto")),
                RELOGIO_SEGUNDA_W48);
        assertThatThrownBy(() -> comManuaisSobrepostos.gerarSemana(CAMINHO_FICTICIO))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("sobrepostos");
    }

    @Test
    void deveGerarSemanaCompletaDaAgendaExemploComDeterminismoEImutabilidade() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository yamlRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()),
                    factory.getValidator());
            AgendaService service = new AgendaService(yamlRepo, RELOGIO_SEGUNDA_W48);

            SemanaPlanejada primeiraExecucao = service.gerarSemana(Path.of("exemplos/agenda.exemplo.yaml"));
            SemanaPlanejada segundaExecucao = service.gerarSemana(Path.of("exemplos/agenda.exemplo.yaml"));

            assertThat(primeiraExecucao).isEqualTo(segundaExecucao);
            assertThat(primeiraExecucao.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 23));
            assertThat(primeiraExecucao.avisos()).isEmpty();

            Semana semana = primeiraExecucao.semana();
            assertThat(semana.ano()).isEqualTo(2026);
            assertThat(semana.numeroIso()).isEqualTo(48);
            assertThat(semana.compromissos()).hasSize(18);

            // Segunda 23/11: 17:45–18:00 (15 min) foi descartado por ser < 25 min
            assertThat(semana.blocos())
                    .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 23)))
                    .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(7, 0), LocalTime.of(8, 30), TipoBloco.PROFUNDO, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(11, 30), LocalTime.of(12, 0), TipoBloco.INTERROMPIVEL, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(13, 0), LocalTime.of(15, 30), TipoBloco.PROFUNDO, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(19, 30), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

            // Quarta 25/11: PLANTAO 15:00–17:00 substituído pelo BlocoManual VIGILANCIA sem duplicação
            assertThat(semana.blocos())
                    .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 25)))
                    .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(7, 0), LocalTime.of(8, 0), TipoBloco.INTERROMPIVEL, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(9, 15), LocalTime.of(10, 30), TipoBloco.INTERROMPIVEL, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(13, 30), LocalTime.of(15, 0), TipoBloco.PROFUNDO, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(15, 0), LocalTime.of(17, 0), TipoBloco.VIGILANCIA,
                                    "Janela de plantão compatível apenas com tarefas leves"),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(17, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

            // Sexta 27/11: bloco manual 10:00–11:30 divide 07:00–12:00 em PROFUNDO + PROFUNDO manual + INTERROMPIVEL
            assertThat(semana.blocos())
                    .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 27)))
                    .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(7, 0), LocalTime.of(10, 0), TipoBloco.PROFUNDO, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(10, 0), LocalTime.of(11, 30), TipoBloco.PROFUNDO,
                                    "Reserva manual para estudo prático"),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(11, 30), LocalTime.of(12, 0), TipoBloco.INTERROMPIVEL, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(13, 0), LocalTime.of(14, 30), TipoBloco.PROFUNDO, null),
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(16, 45), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

            // Domingo 29/11: jornada inteira PROFUNDO
            assertThat(semana.blocos())
                    .filteredOn(b -> b.data().equals(LocalDate.of(2026, 11, 29)))
                    .extracting(Bloco::inicio, Bloco::fim, Bloco::tipo, Bloco::descricao)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    LocalTime.of(7, 0), LocalTime.of(23, 0), TipoBloco.PROFUNDO, null));

            assertThatThrownBy(() -> primeiraExecucao.avisos().add("novo"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> semana.blocos().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> semana.compromissos().clear())
                    .isInstanceOf(UnsupportedOperationException.class);

            // Invariantes de SemanaPlanejada
            LocalDate segunda = LocalDate.of(2026, 11, 23);
            assertThatNullPointerException()
                    .isThrownBy(() -> new SemanaPlanejada(null, semana, List.of()));
            assertThatNullPointerException()
                    .isThrownBy(() -> new SemanaPlanejada(segunda, null, List.of()));
            assertThatNullPointerException()
                    .isThrownBy(() -> new SemanaPlanejada(segunda, semana, null));
            List<String> avisosMutaveis = new ArrayList<>(List.of("a"));
            SemanaPlanejada copiaDefensiva = new SemanaPlanejada(segunda, semana, avisosMutaveis);
            avisosMutaveis.clear();
            assertThat(copiaDefensiva.avisos()).containsExactly("a");
        }
    }

    private static AgendaService criarService(
            Margens margens, List<Compromisso> compromissos, Clock clock) {
        return criarService(JORNADA_PADRAO, margens, CLASSIFICACAO_PADRAO, compromissos, List.of(), clock);
    }

    private static AgendaService criarService(
            Jornada jornada, Margens margens, List<Compromisso> compromissos, Clock clock) {
        return criarService(jornada, margens, CLASSIFICACAO_PADRAO, compromissos, List.of(), clock);
    }

    private static AgendaService criarService(
            Jornada jornada,
            Margens margens,
            Classificacao classificacao,
            List<Compromisso> compromissos,
            List<BlocoManual> blocosManuais,
            Clock clock) {
        AgendaConfiguracao config = new AgendaConfiguracao(
                jornada, margens, classificacao, compromissos, blocosManuais);
        AgendaRepository repoEmMemoria = caminho -> config;
        return new AgendaService(repoEmMemoria, clock);
    }
}
