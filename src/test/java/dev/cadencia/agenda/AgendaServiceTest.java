package dev.cadencia.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.Classificacao;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.Jornada;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.Margens;
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

        AgendaExpandida resultado = service.expandir(CAMINHO_FICTICIO);

        assertThat(resultado.inicioSemana()).isEqualTo(LocalDate.of(2026, 11, 16));
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

    private static AgendaService criarService(
            Margens margens, List<Compromisso> compromissos, Clock clock) {
        AgendaConfiguracao config = new AgendaConfiguracao(
                JORNADA_PADRAO, margens, CLASSIFICACAO_PADRAO, compromissos, List.of());
        AgendaRepository repoEmMemoria = caminho -> config;
        return new AgendaService(repoEmMemoria, clock);
    }
}
