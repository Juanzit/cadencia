package dev.cadencia.agenda;

import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.TipoCompromisso;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço responsável por expandir os compromissos da agenda na semana ISO atual, aplicar margens
 * configuráveis por empresa e gerar deslocamentos automáticos entre locais distintos.
 */
public class AgendaService {

    static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static final long NANOS_POR_DIA = Duration.ofDays(1).toNanos();

    private static final Comparator<Compromisso> ORDEM_COMPROMISSOS = Comparator
            .comparing(Compromisso::diaSemana)
            .thenComparing(Compromisso::inicio)
            .thenComparing(Compromisso::duracao)
            .thenComparing(Compromisso::tipo)
            .thenComparing(Compromisso::empresa, Comparator.nullsFirst(String::compareTo))
            .thenComparing(Compromisso::local, Comparator.nullsFirst(String::compareTo))
            .thenComparing(Compromisso::titulo, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<IntervaloOcupado> ORDEM_INTERVALOS = Comparator
            .comparing(IntervaloOcupado::data)
            .thenComparing(IntervaloOcupado::inicio)
            .thenComparing(IntervaloOcupado::tipo)
            .thenComparing(IntervaloOcupado::duracao)
            .thenComparing(IntervaloOcupado::descricao);

    private final AgendaRepository agendaRepository;
    private final Clock clock;

    public AgendaService(AgendaRepository agendaRepository, Clock clock) {
        this.agendaRepository = Objects.requireNonNull(agendaRepository, "agendaRepository não pode ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock não pode ser nulo");
    }

    /**
     * Carrega a configuração da agenda e expande compromissos, margens e deslocamentos automáticos
     * para a semana ISO corrente (segunda a domingo) no fuso {@code America/Sao_Paulo}.
     */
    public AgendaExpandida expandir(Path caminhoAgenda) {
        Objects.requireNonNull(caminhoAgenda, "caminhoAgenda não pode ser nulo");
        AgendaConfiguracao configuracao = agendaRepository.carregar(caminhoAgenda);

        LocalDate hoje = LocalDate.ofInstant(clock.instant(), FUSO_PADRAO);
        LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = inicioSemana.plusDays(6);

        List<Compromisso> compromissosOrdenados = new ArrayList<>(configuracao.compromissos());
        compromissosOrdenados.sort(ORDEM_COMPROMISSOS);

        List<IntervaloOcupado> intervalos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        Map<DayOfWeek, List<Compromisso>> compromissosPorDia = agruparPorDiaOrdenado(compromissosOrdenados);
        expandirPorDia(compromissosPorDia, configuracao, inicioSemana, fimSemana, intervalos, avisos);

        intervalos.sort(ORDEM_INTERVALOS);
        return new AgendaExpandida(inicioSemana, configuracao, intervalos, avisos);
    }

    private static Map<DayOfWeek, List<Compromisso>> agruparPorDiaOrdenado(
            List<Compromisso> compromissosOrdenados) {
        Map<DayOfWeek, List<Compromisso>> porDia = new EnumMap<>(DayOfWeek.class);
        for (Compromisso compromisso : compromissosOrdenados) {
            porDia.computeIfAbsent(compromisso.diaSemana(), ignored -> new ArrayList<>()).add(compromisso);
        }
        return porDia;
    }

    private void expandirPorDia(
            Map<DayOfWeek, List<Compromisso>> compromissosPorDia,
            AgendaConfiguracao configuracao,
            LocalDate inicioSemana,
            LocalDate fimSemana,
            List<IntervaloOcupado> intervalos,
            List<String> avisos) {

        Map<String, MargemEmpresa> margensPorEmpresa = configuracao.margens().porEmpresa();
        int minutosDeslocamento = configuracao.margens().deslocamentoEntreLocaisMin();
        Duration duracaoDeslocamento = Duration.ofMinutes(minutosDeslocamento);

        for (Map.Entry<DayOfWeek, List<Compromisso>> entrada : compromissosPorDia.entrySet()) {
            List<Compromisso> doDia = entrada.getValue();
            for (Compromisso compromisso : doDia) {
                adicionarCompromissoFatiado(compromisso, inicioSemana, fimSemana, intervalos, avisos);
                if (compromisso.tipo() == TipoCompromisso.AULA) {
                    aplicarMargensAula(compromisso, margensPorEmpresa, inicioSemana, fimSemana, intervalos, avisos);
                }
            }
            if (minutosDeslocamento > 0) {
                gerarDeslocamentosDoDia(
                        dataNaSemana(inicioSemana, entrada.getKey()),
                        doDia,
                        duracaoDeslocamento,
                        inicioSemana,
                        intervalos,
                        avisos);
            }
        }
    }

    private void adicionarCompromissoFatiado(
            Compromisso compromisso,
            LocalDate inicioSemana,
            LocalDate fimSemana,
            List<IntervaloOcupado> intervalos,
            List<String> avisos) {

        String descricao = descreverCompromisso(compromisso);
        List<Compromisso> fragmentos = compromisso.fatiarPorDia();
        LocalDate dataInicial = dataNaSemana(inicioSemana, compromisso.diaSemana());

        for (int i = 0; i < fragmentos.size(); i++) {
            Compromisso fragmento = fragmentos.get(i);
            LocalDate dataFragmento = dataInicial.plusDays(i);
            if (dataFragmento.isAfter(fimSemana)) {
                avisos.add(String.format(
                        "Compromisso \"%s\" de domingo ultrapassa a semana; a fração de segunda-feira (%s–%s) foi descartada.",
                        descricao,
                        fragmento.inicio(),
                        fragmento.fim()));
                continue;
            }
            intervalos.add(new IntervaloOcupado(
                    dataFragmento,
                    fragmento.inicio(),
                    fragmento.duracao(),
                    TipoIntervaloOcupado.COMPROMISSO,
                    descricao));
        }
    }

    private void aplicarMargensAula(
            Compromisso aula,
            Map<String, MargemEmpresa> margensPorEmpresa,
            LocalDate inicioSemana,
            LocalDate fimSemana,
            List<IntervaloOcupado> intervalos,
            List<String> avisos) {

        if (aula.empresa() == null || aula.empresa().isBlank()) {
            return;
        }
        MargemEmpresa margem = margensPorEmpresa.get(aula.empresa());
        if (margem == null) {
            return;
        }

        LocalDate dataAula = dataNaSemana(inicioSemana, aula.diaSemana());
        if (margem.antesMin() > 0) {
            adicionarIntervaloAnterior(
                    dataAula,
                    aula.inicio(),
                    Duration.ofMinutes(margem.antesMin()),
                    TipoIntervaloOcupado.MARGEM_ANTES,
                    "Margem antes (" + aula.empresa() + ")",
                    inicioSemana,
                    intervalos,
                    avisos);
        }
        if (margem.depoisMin() > 0) {
            LocalDate dataFimAula = calcularDataFim(dataAula, aula);
            adicionarIntervaloPosterior(
                    dataFimAula,
                    aula.fim(),
                    Duration.ofMinutes(margem.depoisMin()),
                    TipoIntervaloOcupado.MARGEM_DEPOIS,
                    "Margem depois (" + aula.empresa() + ")",
                    fimSemana,
                    intervalos,
                    avisos);
        }
    }

    private void gerarDeslocamentosDoDia(
            LocalDate dataDia,
            List<Compromisso> compromissosOrdenados,
            Duration duracaoDeslocamento,
            LocalDate inicioSemana,
            List<IntervaloOcupado> intervalos,
            List<String> avisos) {

        String localAnteriorNormalizado = null;
        String localAnteriorExibicao = null;
        boolean houveDeslocamentoExplicito = false;

        for (Compromisso atual : compromissosOrdenados) {
            if (atual.tipo() == TipoCompromisso.DESLOCAMENTO) {
                houveDeslocamentoExplicito = true;
                continue;
            }
            String localAtualNormalizado = normalizarLocal(atual.local());
            if (localAtualNormalizado == null) {
                continue;
            }
            String localAtualExibicao = atual.local().trim();
            if (localAnteriorNormalizado != null
                    && !localAnteriorNormalizado.equals(localAtualNormalizado)
                    && !houveDeslocamentoExplicito) {
                adicionarIntervaloAnterior(
                        dataDia,
                        atual.inicio(),
                        duracaoDeslocamento,
                        TipoIntervaloOcupado.DESLOCAMENTO_AUTOMATICO,
                        "Deslocamento automático (" + localAnteriorExibicao + " -> " + localAtualExibicao + ")",
                        inicioSemana,
                        intervalos,
                        avisos);
            }
            localAnteriorNormalizado = localAtualNormalizado;
            localAnteriorExibicao = localAtualExibicao;
            houveDeslocamentoExplicito = false;
        }
    }

    private void adicionarIntervaloAnterior(
            LocalDate dataReferencia,
            LocalTime horaFim,
            Duration duracaoTotal,
            TipoIntervaloOcupado tipo,
            String descricao,
            LocalDate inicioSemana,
            List<IntervaloOcupado> intervalos,
            List<String> avisos) {

        long restanteNanos = duracaoTotal.toNanos();
        LocalDate dia = dataReferencia;
        long fimNoDiaNanos = horaFim.toNanoOfDay();

        if (fimNoDiaNanos == 0) {
            dia = dia.minusDays(1);
            fimNoDiaNanos = NANOS_POR_DIA;
        }

        while (restanteNanos > 0) {
            if (dia.isBefore(inicioSemana)) {
                avisos.add(String.format(
                        "%s antecede o início da semana; a fração anterior à segunda-feira foi descartada.",
                        descricao));
                break;
            }
            long usadoNanos = Math.min(restanteNanos, fimNoDiaNanos);
            LocalTime inicio = LocalTime.ofNanoOfDay(fimNoDiaNanos - usadoNanos);
            intervalos.add(new IntervaloOcupado(dia, inicio, Duration.ofNanos(usadoNanos), tipo, descricao));
            restanteNanos -= usadoNanos;
            dia = dia.minusDays(1);
            fimNoDiaNanos = NANOS_POR_DIA;
        }
    }

    private void adicionarIntervaloPosterior(
            LocalDate dataReferencia,
            LocalTime horaInicio,
            Duration duracaoTotal,
            TipoIntervaloOcupado tipo,
            String descricao,
            LocalDate fimSemana,
            List<IntervaloOcupado> intervalos,
            List<String> avisos) {

        long restanteNanos = duracaoTotal.toNanos();
        LocalDate dia = dataReferencia;
        long inicioNoDiaNanos = horaInicio.toNanoOfDay();

        while (restanteNanos > 0) {
            if (dia.isAfter(fimSemana)) {
                avisos.add(String.format(
                        "%s ultrapassa o domingo da semana atual; a fração na semana seguinte foi descartada.",
                        descricao));
                break;
            }
            long disponivelNoDiaNanos = NANOS_POR_DIA - inicioNoDiaNanos;
            long usadoNanos = Math.min(restanteNanos, disponivelNoDiaNanos);
            intervalos.add(new IntervaloOcupado(
                    dia,
                    LocalTime.ofNanoOfDay(inicioNoDiaNanos),
                    Duration.ofNanos(usadoNanos),
                    tipo,
                    descricao));
            restanteNanos -= usadoNanos;
            dia = dia.plusDays(1);
            inicioNoDiaNanos = 0;
        }
    }

    private static LocalDate calcularDataFim(LocalDate dataInicio, Compromisso compromisso) {
        if (compromisso.cruzaMeiaNoite() || compromisso.fim().equals(LocalTime.MIDNIGHT)) {
            return dataInicio.plusDays(1);
        }
        return dataInicio;
    }

    private static LocalDate dataNaSemana(LocalDate inicioSemana, DayOfWeek diaSemana) {
        return inicioSemana.plusDays(diaSemana.getValue() - 1L);
    }

    private static String normalizarLocal(String local) {
        if (local == null) {
            return null;
        }
        String limpo = local.trim().toUpperCase(Locale.ROOT);
        if (limpo.isEmpty() || "TRANSITO".equals(limpo)) {
            return null;
        }
        return limpo;
    }

    private static String descreverCompromisso(Compromisso compromisso) {
        List<String> partes = new ArrayList<>();
        partes.add(compromisso.tipo().name());
        if (compromisso.empresa() != null && !compromisso.empresa().isBlank()) {
            partes.add(compromisso.empresa().trim());
        }
        if (compromisso.local() != null && !compromisso.local().isBlank()) {
            partes.add(compromisso.local().trim());
        }
        if (compromisso.titulo() != null && !compromisso.titulo().isBlank()) {
            partes.add(compromisso.titulo().trim());
        }
        return String.join(" · ", partes);
    }
}
