package dev.cadencia.agenda;

import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.BlocoManual;
import dev.cadencia.dominio.Classificacao;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.Jornada;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.dominio.TipoCompromisso;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço responsável por expandir os compromissos da agenda na semana ISO atual, aplicar margens
 * configuráveis por empresa, gerar deslocamentos automáticos entre locais distintos, calcular os
 * intervalos livres da jornada diária e classificar os blocos da {@link Semana}.
 */
public class AgendaService {

    static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static final long NANOS_POR_DIA = 86_400_000_000_000L;
    private static final int DIAS_DA_SEMANA = 7;

    private static final Comparator<Compromisso> ORDEM_COMPROMISSOS = Comparator
            .comparing(Compromisso::diaSemana)
            .thenComparing(Compromisso::inicio)
            .thenComparing(Compromisso::duracao)
            .thenComparing(Compromisso::tipo)
            .thenComparing(Compromisso::empresa, Comparator.nullsFirst(String::compareTo))
            .thenComparing(Compromisso::local, Comparator.nullsFirst(String::compareTo))
            .thenComparing(Compromisso::titulo, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<BlocoManual> ORDEM_BLOCOS_MANUAIS = Comparator
            .comparing(BlocoManual::diaSemana)
            .thenComparing(BlocoManual::inicio)
            .thenComparing(BlocoManual::fim)
            .thenComparing(BlocoManual::tipo)
            .thenComparing(BlocoManual::descricao, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<Bloco> ORDEM_BLOCOS = Comparator
            .comparing(Bloco::data)
            .thenComparing(Bloco::inicio)
            .thenComparing(Bloco::fim)
            .thenComparing(Bloco::tipo)
            .thenComparing(Bloco::descricao, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<IntervaloOcupado> ORDEM_INTERVALOS = Comparator
            .comparing(IntervaloOcupado::data)
            .thenComparing(IntervaloOcupado::inicio)
            .thenComparing(IntervaloOcupado::tipo)
            .thenComparing(IntervaloOcupado::duracao)
            .thenComparing(IntervaloOcupado::descricao);

    private static final Comparator<FaixaNanos> ORDEM_FAIXAS = Comparator
            .comparingLong(FaixaNanos::inicio)
            .thenComparingLong(FaixaNanos::fim);

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

        List<Compromisso> compromissosOrdenados = ordenarCompromissos(configuracao.compromissos());

        List<IntervaloOcupado> intervalos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        Map<DayOfWeek, List<Compromisso>> compromissosPorDia = agruparPorDiaOrdenado(compromissosOrdenados);
        expandirPorDia(compromissosPorDia, configuracao, inicioSemana, fimSemana, intervalos, avisos);

        intervalos.sort(ORDEM_INTERVALOS);
        return new AgendaExpandida(inicioSemana, configuracao, intervalos, avisos);
    }

    /**
     * Calcula os intervalos livres da semana ISO corrente (segunda a domingo) dentro da janela de
     * jornada diária, reutilizando a expansão completa e mesclando ocupações sobrepostas ou adjacentes.
     */
    public AgendaIntervalosLivres calcularIntervalosLivres(Path caminhoAgenda) {
        return calcularIntervalosLivres(expandir(caminhoAgenda));
    }

    /**
     * Classifica os intervalos livres, aplica blocos manuais com precedência sobre {@code PLANTAO}
     * e monta a {@link Semana} ISO completa preservando avisos da expansão e conflitos manuais.
     */
    public SemanaPlanejada gerarSemana(Path caminhoAgenda) {
        AgendaExpandida expandida = expandir(caminhoAgenda);
        AgendaConfiguracao configuracao = expandida.configuracao();
        validarBlocosManuaisSemSobreposicao(configuracao.blocosManuais());

        AgendaIntervalosLivres agendaLivre = calcularIntervalosLivres(expandida);
        LocalDate inicioSemana = expandida.inicioSemana();
        LocalDate fimSemana = inicioSemana.plusDays(DIAS_DA_SEMANA - 1L);

        List<Compromisso> compromissosOrdenados = ordenarCompromissos(configuracao.compromissos());
        Map<LocalDate, List<IntervaloLivre>> livresPorData = agruparLivresPorData(agendaLivre.intervalosLivres());
        Map<LocalDate, List<FaixaNanos>> naoPlantaoPorData = new HashMap<>();
        Map<LocalDate, List<CompromissoFragmento>> plantoesPorData = new HashMap<>();
        classificarFragmentosDeOcupacao(
                expandida, compromissosOrdenados, inicioSemana, fimSemana, naoPlantaoPorData, plantoesPorData);
        Map<DayOfWeek, List<BlocoManual>> manuaisPorDia = agruparBlocosManuaisOrdenadosPorDia(
                configuracao.blocosManuais());

        List<Bloco> blocos = new ArrayList<>();
        List<String> avisos = new ArrayList<>(expandida.avisos());
        Jornada jornada = configuracao.jornada();
        Classificacao classificacao = configuracao.classificacao();

        for (int diaOffset = 0; diaOffset < DIAS_DA_SEMANA; diaOffset++) {
            LocalDate data = inicioSemana.plusDays(diaOffset);
            gerarBlocosDoDia(
                    data,
                    jornada,
                    classificacao,
                    livresPorData.getOrDefault(data, List.of()),
                    ordenarEMesclar(naoPlantaoPorData.getOrDefault(data, List.of())),
                    manuaisPorDia.getOrDefault(data.getDayOfWeek(), List.of()),
                    plantoesPorData.getOrDefault(data, List.of()),
                    blocos,
                    avisos);
        }

        blocos.sort(ORDEM_BLOCOS);
        Semana semana = new Semana(
                inicioSemana.get(IsoFields.WEEK_BASED_YEAR),
                inicioSemana.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                compromissosOrdenados,
                blocos);
        return new SemanaPlanejada(inicioSemana, semana, avisos);
    }

    private AgendaIntervalosLivres calcularIntervalosLivres(AgendaExpandida expandida) {
        Map<LocalDate, List<IntervaloOcupado>> ocupadosPorData = agruparOcupadosPorData(expandida.intervalosOcupados());

        LocalDate inicioSemana = expandida.inicioSemana();
        Jornada jornada = expandida.configuracao().jornada();
        List<IntervaloLivre> intervalosLivres = new ArrayList<>();

        for (int diaOffset = 0; diaOffset < DIAS_DA_SEMANA; diaOffset++) {
            LocalDate data = inicioSemana.plusDays(diaOffset);
            List<IntervaloOcupado> ocupadosDoDia = ocupadosPorData.getOrDefault(data, List.of());
            intervalosLivres.addAll(calcularIntervalosLivresDoDia(data, jornada, ocupadosDoDia));
        }

        return new AgendaIntervalosLivres(
                inicioSemana,
                expandida.configuracao(),
                intervalosLivres,
                expandida.avisos());
    }

    private static List<Compromisso> ordenarCompromissos(List<Compromisso> compromissos) {
        List<Compromisso> copia = new ArrayList<>(compromissos);
        copia.sort(ORDEM_COMPROMISSOS);
        return copia;
    }

    private static void validarBlocosManuaisSemSobreposicao(List<BlocoManual> blocosManuais) {
        if (blocosManuais.size() < 2) {
            return;
        }
        Map<DayOfWeek, List<BlocoManual>> porDia = agruparBlocosManuaisOrdenadosPorDia(blocosManuais);
        for (List<BlocoManual> doDia : porDia.values()) {
            BlocoManual anteriorMaiorFim = doDia.get(0);
            for (int i = 1; i < doDia.size(); i++) {
                BlocoManual atual = doDia.get(i);
                if (atual.inicio().isBefore(anteriorMaiorFim.fim())) {
                    throw new DadosInvalidosException(String.format(
                            "Blocos manuais sobrepostos em %s: (%s–%s) e (%s–%s).",
                            atual.diaSemana(),
                            anteriorMaiorFim.inicio(),
                            anteriorMaiorFim.fim(),
                            atual.inicio(),
                            atual.fim()));
                }
                if (atual.fim().isAfter(anteriorMaiorFim.fim())) {
                    anteriorMaiorFim = atual;
                }
            }
        }
    }

    private static Map<DayOfWeek, List<BlocoManual>> agruparBlocosManuaisOrdenadosPorDia(
            List<BlocoManual> blocosManuais) {
        List<BlocoManual> copiaOrdenada = new ArrayList<>(blocosManuais);
        copiaOrdenada.sort(ORDEM_BLOCOS_MANUAIS);
        Map<DayOfWeek, List<BlocoManual>> porDia = new EnumMap<>(DayOfWeek.class);
        for (BlocoManual blocoManual : copiaOrdenada) {
            porDia.computeIfAbsent(blocoManual.diaSemana(), ignored -> new ArrayList<>()).add(blocoManual);
        }
        return porDia;
    }

    private static Map<LocalDate, List<IntervaloLivre>> agruparLivresPorData(List<IntervaloLivre> intervalosLivres) {
        Map<LocalDate, List<IntervaloLivre>> porData = new HashMap<>();
        for (IntervaloLivre intervalo : intervalosLivres) {
            porData.computeIfAbsent(intervalo.data(), ignored -> new ArrayList<>()).add(intervalo);
        }
        return porData;
    }

    private static void classificarFragmentosDeOcupacao(
            AgendaExpandida expandida,
            List<Compromisso> compromissosOrdenados,
            LocalDate inicioSemana,
            LocalDate fimSemana,
            Map<LocalDate, List<FaixaNanos>> naoPlantaoPorData,
            Map<LocalDate, List<CompromissoFragmento>> plantoesPorData) {

        for (Compromisso compromisso : compromissosOrdenados) {
            List<Compromisso> fragmentos = compromisso.fatiarPorDia();
            LocalDate dataInicial = dataNaSemana(inicioSemana, compromisso.diaSemana());
            for (int i = 0; i < fragmentos.size(); i++) {
                LocalDate dataFragmento = dataInicial.plusDays(i);
                if (dataFragmento.isAfter(fimSemana)) {
                    continue;
                }
                Compromisso fragmento = fragmentos.get(i);
                long inicioNanos = fragmento.inicio().toNanoOfDay();
                long fimNanos = Math.min(inicioNanos + fragmento.duracao().toNanos(), NANOS_POR_DIA);
                FaixaNanos faixa = new FaixaNanos(inicioNanos, fimNanos);
                if (compromisso.tipo() == TipoCompromisso.PLANTAO) {
                    plantoesPorData.computeIfAbsent(dataFragmento, ignored -> new ArrayList<>())
                            .add(new CompromissoFragmento(faixa, descreverCompromisso(compromisso)));
                } else {
                    naoPlantaoPorData.computeIfAbsent(dataFragmento, ignored -> new ArrayList<>()).add(faixa);
                }
            }
        }

        for (IntervaloOcupado ocupado : expandida.intervalosOcupados()) {
            if (ocupado.tipo() != TipoIntervaloOcupado.COMPROMISSO) {
                FaixaNanos faixa = new FaixaNanos(ocupado.inicio().toNanoOfDay(), fimEmNanosNoDia(ocupado));
                naoPlantaoPorData.computeIfAbsent(ocupado.data(), ignored -> new ArrayList<>()).add(faixa);
            }
        }
    }

    private static void gerarBlocosDoDia(
            LocalDate data,
            Jornada jornada,
            Classificacao classificacao,
            List<IntervaloLivre> livresDoDia,
            List<FaixaNanos> naoPlantaoMescladas,
            List<BlocoManual> manuaisDoDia,
            List<CompromissoFragmento> plantoesDoDia,
            List<Bloco> blocos,
            List<String> avisos) {

        long inicioJanelaNanos = jornada.inicio().toNanoOfDay();
        long fimJanelaNanos = jornada.fim().toNanoOfDay();

        List<FaixaNanos> faixasManuaisEfetivas = processarBlocosManuaisDoDia(
                data, inicioJanelaNanos, fimJanelaNanos, naoPlantaoMescladas, manuaisDoDia, blocos, avisos);
        List<FaixaNanos> manuaisMescladas = ordenarEMesclar(faixasManuaisEfetivas);

        classificarIntervalosLivresDoDia(data, classificacao, livresDoDia, manuaisMescladas, blocos);
        gerarBlocosPlantaoDoDia(
                data,
                inicioJanelaNanos,
                fimJanelaNanos,
                naoPlantaoMescladas,
                manuaisMescladas,
                plantoesDoDia,
                blocos);
    }

    private static List<FaixaNanos> processarBlocosManuaisDoDia(
            LocalDate data,
            long inicioJanelaNanos,
            long fimJanelaNanos,
            List<FaixaNanos> naoPlantaoMescladas,
            List<BlocoManual> manuaisDoDia,
            List<Bloco> blocos,
            List<String> avisos) {

        List<FaixaNanos> efetivas = new ArrayList<>();
        for (BlocoManual manual : manuaisDoDia) {
            long inicioOriginal = manual.inicio().toNanoOfDay();
            long fimOriginal = manual.fim().toNanoOfDay();
            long inicioRecortado = Math.max(inicioOriginal, inicioJanelaNanos);
            long fimRecortado = Math.min(fimOriginal, fimJanelaNanos);

            List<FaixaNanos> fatiasValidas = inicioRecortado < fimRecortado
                    ? subtrairFaixas(inicioRecortado, fimRecortado, naoPlantaoMescladas)
                    : List.of();

            long duracaoOriginal = fimOriginal - inicioOriginal;
            long duracaoValida = fatiasValidas.stream().mapToLong(FaixaNanos::duracaoNanos).sum();
            if (duracaoValida == 0) {
                avisos.add(String.format(
                        "Bloco manual %s em %s (%s–%s) foi descartado por conflito com ocupação não-plantão ou por estar fora da jornada.",
                        descreverBlocoManual(manual), data, manual.inicio(), manual.fim()));
                continue;
            }
            if (duracaoValida < duracaoOriginal) {
                avisos.add(String.format(
                        "Bloco manual %s em %s (%s–%s) foi recortado por conflito com ocupação não-plantão ou limites da jornada.",
                        descreverBlocoManual(manual), data, manual.inicio(), manual.fim()));
            }
            for (FaixaNanos fatia : fatiasValidas) {
                efetivas.add(fatia);
                blocos.add(new Bloco(
                        data,
                        LocalTime.ofNanoOfDay(fatia.inicio()),
                        LocalTime.ofNanoOfDay(fatia.fim()),
                        manual.tipo(),
                        manual.descricao()));
            }
        }
        return efetivas;
    }

    private static void classificarIntervalosLivresDoDia(
            LocalDate data,
            Classificacao classificacao,
            List<IntervaloLivre> livresDoDia,
            List<FaixaNanos> manuaisMescladas,
            List<Bloco> blocos) {

        Duration minimoProfundo = Duration.ofMinutes(classificacao.profundoMinimoMin());
        Duration minimoInterrompivel = Duration.ofMinutes(classificacao.interrompivelMinimoMin());

        for (IntervaloLivre livre : livresDoDia) {
            List<FaixaNanos> fatiasLivres = subtrairFaixas(
                    livre.inicio().toNanoOfDay(), livre.fim().toNanoOfDay(), manuaisMescladas);
            for (FaixaNanos fatia : fatiasLivres) {
                Duration duracao = Duration.ofNanos(fatia.duracaoNanos());
                TipoBloco tipoClassificado;
                if (duracao.compareTo(minimoProfundo) >= 0) {
                    tipoClassificado = TipoBloco.PROFUNDO;
                } else if (duracao.compareTo(minimoInterrompivel) >= 0) {
                    tipoClassificado = TipoBloco.INTERROMPIVEL;
                } else {
                    continue;
                }
                blocos.add(new Bloco(
                        data,
                        LocalTime.ofNanoOfDay(fatia.inicio()),
                        LocalTime.ofNanoOfDay(fatia.fim()),
                        tipoClassificado,
                        null));
            }
        }
    }

    private static void gerarBlocosPlantaoDoDia(
            LocalDate data,
            long inicioJanelaNanos,
            long fimJanelaNanos,
            List<FaixaNanos> naoPlantaoMescladas,
            List<FaixaNanos> manuaisMescladas,
            List<CompromissoFragmento> plantoesDoDia,
            List<Bloco> blocos) {

        if (plantoesDoDia.isEmpty()) {
            return;
        }
        List<FaixaNanos> bloqueios = combinarEMesclar(naoPlantaoMescladas, manuaisMescladas);
        for (CompromissoFragmento plantao : plantoesDoDia) {
            long inicioRecortado = Math.max(plantao.faixa().inicio(), inicioJanelaNanos);
            long fimRecortado = Math.min(plantao.faixa().fim(), fimJanelaNanos);
            if (inicioRecortado >= fimRecortado) {
                continue;
            }
            List<FaixaNanos> restantes = subtrairFaixas(inicioRecortado, fimRecortado, bloqueios);
            if (restantes.isEmpty()) {
                continue;
            }
            for (FaixaNanos fatia : restantes) {
                blocos.add(new Bloco(
                        data,
                        LocalTime.ofNanoOfDay(fatia.inicio()),
                        LocalTime.ofNanoOfDay(fatia.fim()),
                        TipoBloco.VIGILANCIA,
                        plantao.descricao()));
            }
            bloqueios = combinarEMesclar(bloqueios, restantes);
        }
    }

    private static List<FaixaNanos> combinarEMesclar(List<FaixaNanos> primeira, List<FaixaNanos> segunda) {
        if (primeira.isEmpty() && segunda.isEmpty()) {
            return List.of();
        }
        List<FaixaNanos> combinadas = new ArrayList<>(primeira.size() + segunda.size());
        combinadas.addAll(primeira);
        combinadas.addAll(segunda);
        return ordenarEMesclar(combinadas);
    }

    private static List<FaixaNanos> ordenarEMesclar(List<FaixaNanos> faixas) {
        if (faixas.isEmpty()) {
            return List.of();
        }
        List<FaixaNanos> ordenadas = new ArrayList<>(faixas);
        ordenadas.sort(ORDEM_FAIXAS);
        return mesclarSobreposicoesEAdjacencias(ordenadas);
    }

    private static String descreverBlocoManual(BlocoManual manual) {
        if (manual.descricao() != null && !manual.descricao().isBlank()) {
            return "\"" + manual.descricao().trim() + "\" (" + manual.tipo() + ")";
        }
        return manual.tipo().name();
    }

    private static Map<LocalDate, List<IntervaloOcupado>> agruparOcupadosPorData(
            List<IntervaloOcupado> intervalosOcupados) {
        Map<LocalDate, List<IntervaloOcupado>> porData = new HashMap<>();
        for (IntervaloOcupado intervalo : intervalosOcupados) {
            porData.computeIfAbsent(intervalo.data(), ignored -> new ArrayList<>()).add(intervalo);
        }
        return porData;
    }

    private static List<IntervaloLivre> calcularIntervalosLivresDoDia(
            LocalDate data, Jornada jornada, List<IntervaloOcupado> ocupadosDoDia) {
        long inicioJanelaNanos = jornada.inicio().toNanoOfDay();
        long fimJanelaNanos = jornada.fim().toNanoOfDay();

        List<FaixaNanos> recortados = recortarOcupacoesNaJanela(ocupadosDoDia, inicioJanelaNanos, fimJanelaNanos);
        if (recortados.isEmpty()) {
            return List.of(new IntervaloLivre(data, jornada.inicio(), jornada.fim()));
        }

        List<FaixaNanos> ocupacoesMescladas = ordenarEMesclar(recortados);
        return subtrairOcupacoesDaJanela(data, inicioJanelaNanos, fimJanelaNanos, ocupacoesMescladas);
    }

    private static List<FaixaNanos> recortarOcupacoesNaJanela(
            List<IntervaloOcupado> ocupadosDoDia, long inicioJanelaNanos, long fimJanelaNanos) {
        List<FaixaNanos> recortados = new ArrayList<>();
        for (IntervaloOcupado ocupado : ocupadosDoDia) {
            long inicioOcupadoNanos = ocupado.inicio().toNanoOfDay();
            long fimOcupadoNanos = fimEmNanosNoDia(ocupado);

            long inicioRecortado = Math.max(inicioOcupadoNanos, inicioJanelaNanos);
            long fimRecortado = Math.min(fimOcupadoNanos, fimJanelaNanos);
            if (inicioRecortado < fimRecortado) {
                recortados.add(new FaixaNanos(inicioRecortado, fimRecortado));
            }
        }
        return recortados;
    }

    private static List<FaixaNanos> mesclarSobreposicoesEAdjacencias(List<FaixaNanos> faixasOrdenadas) {
        List<FaixaNanos> mesclados = new ArrayList<>();
        long atualInicio = faixasOrdenadas.get(0).inicio();
        long atualFim = faixasOrdenadas.get(0).fim();

        for (int i = 1; i < faixasOrdenadas.size(); i++) {
            FaixaNanos proxima = faixasOrdenadas.get(i);
            if (proxima.inicio() <= atualFim) {
                atualFim = Math.max(atualFim, proxima.fim());
            } else {
                mesclados.add(new FaixaNanos(atualInicio, atualFim));
                atualInicio = proxima.inicio();
                atualFim = proxima.fim();
            }
        }
        mesclados.add(new FaixaNanos(atualInicio, atualFim));
        return mesclados;
    }

    private static List<IntervaloLivre> subtrairOcupacoesDaJanela(
            LocalDate data, long inicioJanelaNanos, long fimJanelaNanos, List<FaixaNanos> ocupacoesMescladas) {
        return subtrairFaixas(inicioJanelaNanos, fimJanelaNanos, ocupacoesMescladas).stream()
                .map(faixa -> new IntervaloLivre(
                        data,
                        LocalTime.ofNanoOfDay(faixa.inicio()),
                        LocalTime.ofNanoOfDay(faixa.fim())))
                .toList();
    }

    private static List<FaixaNanos> subtrairFaixas(
            long alvoInicio, long alvoFim, List<FaixaNanos> bloqueiosMesclados) {
        List<FaixaNanos> restantes = new ArrayList<>();
        long cursorNanos = alvoInicio;

        for (FaixaNanos bloqueio : bloqueiosMesclados) {
            if (bloqueio.fim() <= cursorNanos) {
                continue;
            }
            if (bloqueio.inicio() >= alvoFim) {
                break;
            }
            if (bloqueio.inicio() > cursorNanos) {
                restantes.add(new FaixaNanos(cursorNanos, bloqueio.inicio()));
            }
            cursorNanos = Math.max(cursorNanos, bloqueio.fim());
            if (cursorNanos >= alvoFim) {
                break;
            }
        }

        if (cursorNanos < alvoFim) {
            restantes.add(new FaixaNanos(cursorNanos, alvoFim));
        }
        return restantes;
    }

    private static long fimEmNanosNoDia(IntervaloOcupado ocupado) {
        // IntervaloOcupado garante termino <= 24h; somar inicio + duracao preserva 00:00 como 24h em nanos.
        return Math.min(ocupado.inicio().toNanoOfDay() + ocupado.duracao().toNanos(), NANOS_POR_DIA);
    }

    private record FaixaNanos(long inicio, long fim) {
        long duracaoNanos() {
            return fim - inicio;
        }
    }

    private record CompromissoFragmento(FaixaNanos faixa, String descricao) {
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
