package dev.cadencia.exportacao.strategies;

import dev.cadencia.agenda.IntervaloOcupado;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.agenda.TipoIntervaloOcupado;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.PedacoAlocado;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Exporta uma {@link SemanaPlanejada} (opcionalmente acompanhada de um {@link ResultadoAlocacao})
 * para Markdown padrão no formato especificado em {@code dev/DESIGN.md}, garantindo saída
 * determinística, idempotente e com quebras de linha LF.
 */
public class MarkdownSemanalExportador implements ExportadorStrategy {

    private static final String LF = "\n";
    private static final long NANOS_POR_DIA = 86_400_000_000_000L;
    private static final int DIAS_DA_SEMANA = 7;

    private static final DateTimeFormatter FORMATADOR_DATA_CURTA =
            DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);
    private static final DateTimeFormatter FORMATADOR_HORA =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static final Comparator<Bloco> ORDEM_BLOCOS = Comparator
            .comparing(Bloco::data)
            .thenComparing(Bloco::inicio)
            .thenComparing(Bloco::fim)
            .thenComparing(Bloco::tipo)
            .thenComparing(Bloco::descricao, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<PedacoAlocado> ORDEM_ALOCADOS = Comparator
            .comparing(PedacoAlocado::data)
            .thenComparing(PedacoAlocado::inicio)
            .thenComparing(PedacoAlocado::fim);

    private static final Comparator<LinhaDia> ORDEM_LINHAS_DIA = Comparator
            .comparingLong(LinhaDia::inicioNanos)
            .thenComparingLong(LinhaDia::fimNanos)
            .thenComparingInt(LinhaDia::ordemCategoria)
            .thenComparing(LinhaDia::tipo)
            .thenComparing(LinhaDia::detalhe);

    @Override
    public String exportar(SemanaPlanejada semanaPlanejada) {
        Objects.requireNonNull(semanaPlanejada, "semanaPlanejada não pode ser nula");
        return exportarInterno(semanaPlanejada, null);
    }

    @Override
    public String exportar(SemanaPlanejada semanaPlanejada, ResultadoAlocacao resultadoAlocacao) {
        Objects.requireNonNull(semanaPlanejada, "semanaPlanejada não pode ser nula");
        Objects.requireNonNull(resultadoAlocacao, "resultadoAlocacao não pode ser nulo");
        return exportarInterno(semanaPlanejada, resultadoAlocacao);
    }

    private static String exportarInterno(
            SemanaPlanejada semanaPlanejada, ResultadoAlocacao resultadoAlocacao) {
        LocalDate inicioSemana = semanaPlanejada.inicioSemana();
        LocalDate fimSemana = inicioSemana.plusDays(DIAS_DA_SEMANA - 1L);
        Semana semana = semanaPlanejada.semana();

        List<Bloco> blocosOrdenados = new ArrayList<>(semana.blocos());
        blocosOrdenados.sort(ORDEM_BLOCOS);

        List<PedacoAlocado> alocadosOrdenados = resultadoAlocacao != null
                ? resultadoAlocacao.alocados().stream().sorted(ORDEM_ALOCADOS).toList()
                : List.of();

        Duration duracaoMinimaResidual = duracaoMinimaResidual(semanaPlanejada);

        StringBuilder md = new StringBuilder();
        escreverCabecalho(md, semana, inicioSemana, fimSemana);
        escreverResumo(md, blocosOrdenados, alocadosOrdenados);
        escreverDiasDaSemana(
                md,
                inicioSemana,
                semanaPlanejada.intervalosOcupados(),
                blocosOrdenados,
                alocadosOrdenados,
                duracaoMinimaResidual);
        escreverChecklist(md, blocosOrdenados, alocadosOrdenados);
        escreverAvisos(md, semanaPlanejada.avisos(), resultadoAlocacao);
        return md.toString();
    }

    private static Duration duracaoMinimaResidual(SemanaPlanejada semanaPlanejada) {
        if (semanaPlanejada.configuracao() != null) {
            return Duration.ofMinutes(
                    semanaPlanejada.configuracao().classificacao().interrompivelMinimoMin());
        }
        return Tarefa.TAMANHO_PEDACO_PADRAO;
    }

    private static void escreverCabecalho(
            StringBuilder md, Semana semana, LocalDate inicioSemana, LocalDate fimSemana) {
        md.append("# Semana ")
                .append(String.format(Locale.ROOT, "%04d-W%02d", semana.ano(), semana.numeroIso()))
                .append(" (")
                .append(FORMATADOR_DATA_CURTA.format(inicioSemana))
                .append(" a ")
                .append(FORMATADOR_DATA_CURTA.format(fimSemana))
                .append(")")
                .append(LF)
                .append(LF);
    }

    private static void escreverResumo(
            StringBuilder md, List<Bloco> blocosOrdenados, List<PedacoAlocado> alocadosOrdenados) {
        long profundos = 0;
        long interrompiveis = 0;
        long vigilancias = 0;
        Duration tempoTotal = Duration.ZERO;

        for (Bloco bloco : blocosOrdenados) {
            tempoTotal = tempoTotal.plus(Duration.between(bloco.inicio(), bloco.fim()));
            switch (bloco.tipo()) {
                case PROFUNDO -> profundos++;
                case INTERROMPIVEL -> interrompiveis++;
                case VIGILANCIA -> vigilancias++;
            }
        }

        md.append("## Resumo").append(LF)
                .append(String.format(
                        Locale.ROOT,
                        "- Blocos livres: %d (profundo: %d · interrompível: %d · vigilância: %d)",
                        blocosOrdenados.size(), profundos, interrompiveis, vigilancias))
                .append(LF)
                .append("- Tempo livre total: ").append(formatarDuracao(tempoTotal)).append(LF)
                .append("- Turmas em transição: nenhuma").append(LF);

        if (!alocadosOrdenados.isEmpty()) {
            Duration tempoAlocado = alocadosOrdenados.stream()
                    .map(alocado -> alocado.pedaco().duracao())
                    .reduce(Duration.ZERO, Duration::plus);
            md.append(String.format(
                            Locale.ROOT,
                            "- Pedaços alocados: %d (%s)",
                            alocadosOrdenados.size(),
                            formatarDuracao(tempoAlocado)))
                    .append(LF);
        }
    }

    private static void escreverDiasDaSemana(
            StringBuilder md,
            LocalDate inicioSemana,
            List<IntervaloOcupado> intervalosOcupados,
            List<Bloco> blocosOrdenados,
            List<PedacoAlocado> alocadosOrdenados,
            Duration duracaoMinimaResidual) {

        Map<LocalDate, List<LinhaDia>> linhasPorDia = agruparLinhasPorDia(
                intervalosOcupados, blocosOrdenados, alocadosOrdenados, duracaoMinimaResidual);

        for (int diaOffset = 0; diaOffset < DIAS_DA_SEMANA; diaOffset++) {
            LocalDate data = inicioSemana.plusDays(diaOffset);
            md.append(LF)
                    .append("## ")
                    .append(nomeDiaPorExtenso(data.getDayOfWeek()))
                    .append(", ")
                    .append(FORMATADOR_DATA_CURTA.format(data))
                    .append(LF);

            List<LinhaDia> linhas = linhasPorDia.getOrDefault(data, List.of());
            if (linhas.isEmpty()) {
                md.append("- Sem compromissos ou blocos livres registrados.").append(LF);
                continue;
            }

            md.append("| Horário | Tipo | Detalhe |").append(LF)
                    .append("| :--- | :--- | :--- |").append(LF);
            for (LinhaDia linha : linhas) {
                md.append("| ")
                        .append(linha.horario())
                        .append(" | ")
                        .append(sanitizarCelula(linha.tipo()))
                        .append(" | ")
                        .append(sanitizarCelula(linha.detalhe()))
                        .append(" |")
                        .append(LF);
            }
        }
    }

    private static Map<LocalDate, List<LinhaDia>> agruparLinhasPorDia(
            List<IntervaloOcupado> intervalosOcupados,
            List<Bloco> blocosOrdenados,
            List<PedacoAlocado> alocadosOrdenados,
            Duration duracaoMinimaResidual) {
        Map<LocalDate, List<LinhaDia>> porDia = new HashMap<>();

        for (IntervaloOcupado ocupado : intervalosOcupados) {
            porDia.computeIfAbsent(ocupado.data(), ignored -> new ArrayList<>())
                    .add(linhaDeOcupacao(ocupado));
        }

        for (PedacoAlocado alocado : alocadosOrdenados) {
            porDia.computeIfAbsent(alocado.data(), ignored -> new ArrayList<>())
                    .add(linhaDePedacoAlocado(alocado));
        }

        Map<Integer, List<PedacoAlocado>> alocadosPorBloco = associarAlocadosAosBlocos(
                blocosOrdenados, alocadosOrdenados);

        for (int indiceBloco = 0; indiceBloco < blocosOrdenados.size(); indiceBloco++) {
            Bloco bloco = blocosOrdenados.get(indiceBloco);
            List<PedacoAlocado> alocadosNoBloco = alocadosPorBloco.getOrDefault(indiceBloco, List.of());
            if (alocadosNoBloco.isEmpty()) {
                porDia.computeIfAbsent(bloco.data(), ignored -> new ArrayList<>())
                        .add(linhaDeBloco(bloco));
            } else {
                adicionarBlocosResiduais(alocadosNoBloco, duracaoMinimaResidual, porDia);
            }
        }

        for (List<LinhaDia> linhas : porDia.values()) {
            linhas.sort(ORDEM_LINHAS_DIA);
        }
        return porDia;
    }

    private static Map<Integer, List<PedacoAlocado>> associarAlocadosAosBlocos(
            List<Bloco> blocosOrdenados, List<PedacoAlocado> alocadosOrdenados) {
        if (alocadosOrdenados.isEmpty() || blocosOrdenados.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<PedacoAlocado>> porIndiceBloco = new HashMap<>();
        boolean[] associados = new boolean[alocadosOrdenados.size()];

        // Associa primeiro por igualdade exata com blocoOrigem, depois por subintervalo ajustado por folga.
        for (int i = 0; i < alocadosOrdenados.size(); i++) {
            Bloco origem = alocadosOrdenados.get(i).blocoOrigem();
            for (int b = 0; b < blocosOrdenados.size(); b++) {
                if (origem.equals(blocosOrdenados.get(b))) {
                    porIndiceBloco.computeIfAbsent(b, ignored -> new ArrayList<>())
                            .add(alocadosOrdenados.get(i));
                    associados[i] = true;
                    break;
                }
            }
        }

        for (int i = 0; i < alocadosOrdenados.size(); i++) {
            if (associados[i]) {
                continue;
            }
            Bloco origem = alocadosOrdenados.get(i).blocoOrigem();
            for (int b = 0; b < blocosOrdenados.size(); b++) {
                if (pertenceAoBloco(origem, blocosOrdenados.get(b))) {
                    porIndiceBloco.computeIfAbsent(b, ignored -> new ArrayList<>())
                            .add(alocadosOrdenados.get(i));
                    associados[i] = true;
                    break;
                }
            }
        }
        return porIndiceBloco;
    }

    private static boolean pertenceAoBloco(Bloco blocoOrigem, Bloco candidato) {
        return blocoOrigem.data().equals(candidato.data())
                && blocoOrigem.tipo() == candidato.tipo()
                && Objects.equals(blocoOrigem.descricao(), candidato.descricao())
                && !blocoOrigem.inicio().isBefore(candidato.inicio())
                && !blocoOrigem.fim().isAfter(candidato.fim());
    }

    private static void adicionarBlocosResiduais(
            List<PedacoAlocado> alocadosNoBloco,
            Duration duracaoMinimaResidual,
            Map<LocalDate, List<LinhaDia>> porDia) {
        Bloco base = alocadosNoBloco.get(0).blocoOrigem();
        LocalTime cursor = base.inicio();

        for (PedacoAlocado alocado : alocadosNoBloco) {
            if (alocado.inicio().isAfter(cursor)) {
                adicionarSeRelevante(base, cursor, alocado.inicio(), duracaoMinimaResidual, porDia);
            }
            if (alocado.fim().isAfter(cursor)) {
                cursor = alocado.fim();
            }
        }
        if (cursor.isBefore(base.fim())) {
            adicionarSeRelevante(base, cursor, base.fim(), duracaoMinimaResidual, porDia);
        }
    }

    private static void adicionarSeRelevante(
            Bloco base,
            LocalTime inicio,
            LocalTime fim,
            Duration duracaoMinimaResidual,
            Map<LocalDate, List<LinhaDia>> porDia) {
        if (Duration.between(inicio, fim).compareTo(duracaoMinimaResidual) < 0) {
            return;
        }
        Bloco residual = new Bloco(base.data(), inicio, fim, base.tipo(), base.descricao());
        porDia.computeIfAbsent(base.data(), ignored -> new ArrayList<>()).add(linhaDeBloco(residual));
    }

    private static LinhaDia linhaDeOcupacao(IntervaloOcupado ocupado) {
        long inicioNanos = ocupado.inicio().toNanoOfDay();
        long fimNanos = Math.min(inicioNanos + ocupado.duracao().toNanos(), NANOS_POR_DIA);
        String horario = formatarIntervalo(ocupado.inicio(), ocupado.fim());
        String tipo = formatarTipoOcupacao(ocupado);
        String detalhe = formatarDetalheOcupacao(ocupado);
        return new LinhaDia(inicioNanos, fimNanos, 0, horario, tipo, detalhe);
    }

    private static LinhaDia linhaDePedacoAlocado(PedacoAlocado alocado) {
        return new LinhaDia(
                alocado.inicio().toNanoOfDay(),
                alocado.fim().toNanoOfDay(),
                1,
                formatarIntervalo(alocado.inicio(), alocado.fim()),
                rotuloTabelaBloco(alocado.blocoOrigem().tipo()),
                formatarTituloPedaco(alocado.pedaco()));
    }

    private static LinhaDia linhaDeBloco(Bloco bloco) {
        long inicioNanos = bloco.inicio().toNanoOfDay();
        long fimNanos = bloco.fim().toNanoOfDay();
        String horario = formatarIntervalo(bloco.inicio(), bloco.fim());
        String tipo = rotuloTabelaBloco(bloco.tipo());
        String detalhe = temTexto(bloco.descricao())
                ? bloco.descricao().trim()
                : "Disponível (" + formatarDuracao(Duration.between(bloco.inicio(), bloco.fim())) + ")";
        return new LinhaDia(inicioNanos, fimNanos, 1, horario, tipo, detalhe);
    }

    private static void escreverChecklist(
            StringBuilder md,
            List<Bloco> blocosOrdenados,
            List<PedacoAlocado> alocadosOrdenados) {
        md.append(LF).append("## Checklist de estudos").append(LF);
        if (!alocadosOrdenados.isEmpty()) {
            for (PedacoAlocado alocado : alocadosOrdenados) {
                md.append("- [ ] ")
                        .append(abreviacaoDia(alocado.data().getDayOfWeek()))
                        .append(' ')
                        .append(formatarIntervalo(alocado.inicio(), alocado.fim()))
                        .append(" · ")
                        .append(sanitizarLinha(formatarTituloPedaco(alocado.pedaco())))
                        .append(LF);
            }
            return;
        }

        if (blocosOrdenados.isEmpty()) {
            md.append("- Nenhum bloco livre disponível na semana.").append(LF);
            return;
        }
        for (Bloco bloco : blocosOrdenados) {
            String descricaoItem = temTexto(bloco.descricao())
                    ? sanitizarLinha(bloco.descricao())
                    : rotuloSimplesBloco(bloco.tipo()) + " ("
                            + formatarDuracao(Duration.between(bloco.inicio(), bloco.fim())) + ")";
            md.append("- [ ] ")
                    .append(abreviacaoDia(bloco.data().getDayOfWeek()))
                    .append(' ')
                    .append(formatarIntervalo(bloco.inicio(), bloco.fim()))
                    .append(" · ")
                    .append(descricaoItem)
                    .append(LF);
        }
    }

    private static void escreverAvisos(
            StringBuilder md, List<String> avisosSemana, ResultadoAlocacao resultadoAlocacao) {
        List<String> todosAvisos = new ArrayList<>(avisosSemana);
        if (resultadoAlocacao != null && !resultadoAlocacao.naoAlocados().isEmpty()) {
            Map<String, List<String>> porTarefa = new LinkedHashMap<>();
            for (Pedaco pedaco : resultadoAlocacao.naoAlocados()) {
                porTarefa.computeIfAbsent(pedaco.tarefa().titulo(), ignored -> new ArrayList<>())
                        .add(pedaco.numero() + "/" + pedaco.total());
            }
            for (Map.Entry<String, List<String>> entry : porTarefa.entrySet()) {
                todosAvisos.add(String.format(
                        "Não foi possível alocar todos os pedaços de \"%s\" (%s) por falta de blocos compatíveis ou tempo disponível.",
                        entry.getKey(),
                        String.join(", ", entry.getValue())));
            }
        }

        md.append(LF).append("## Avisos").append(LF);
        if (todosAvisos.isEmpty()) {
            md.append("- Nenhum aviso.").append(LF);
            return;
        }
        for (String aviso : todosAvisos) {
            md.append("- ").append(sanitizarLinha(aviso)).append(LF);
        }
    }

    private static String formatarTituloPedaco(Pedaco pedaco) {
        return pedaco.tarefa().titulo().trim() + " (" + pedaco.numero() + "/" + pedaco.total() + ")";
    }

    private static String formatarIntervalo(LocalTime inicio, LocalTime fim) {
        return FORMATADOR_HORA.format(inicio) + "–" + FORMATADOR_HORA.format(fim);
    }

    private static String formatarDuracao(Duration duracao) {
        long minutosTotais = duracao.toMinutes();
        long horas = minutosTotais / 60;
        long minutos = minutosTotais % 60;
        return String.format(Locale.ROOT, "%dh%02d", horas, minutos);
    }

    private static String formatarTipoOcupacao(IntervaloOcupado ocupado) {
        return switch (ocupado.tipo()) {
            case MARGEM_ANTES, MARGEM_DEPOIS -> "Margem";
            case DESLOCAMENTO_AUTOMATICO -> "Deslocamento";
            case COMPROMISSO -> traduzirTipoCompromisso(extrairPrefixoCompromisso(ocupado.descricao()));
        };
    }

    private static String formatarDetalheOcupacao(IntervaloOcupado ocupado) {
        if (ocupado.tipo() != TipoIntervaloOcupado.COMPROMISSO) {
            return ocupado.descricao().trim();
        }
        String descricao = ocupado.descricao().trim();
        int separador = descricao.indexOf(" · ");
        if (separador < 0) {
            return "—";
        }
        String restante = descricao.substring(separador + 3).trim();
        return restante.isEmpty() ? "—" : restante;
    }

    private static String extrairPrefixoCompromisso(String descricao) {
        String limpa = descricao.trim();
        int separador = limpa.indexOf(" · ");
        return separador < 0 ? limpa : limpa.substring(0, separador);
    }

    private static String traduzirTipoCompromisso(String codigo) {
        return switch (codigo) {
            case "AULA" -> "Aula";
            case "REUNIAO" -> "Reunião";
            case "ALMOCO" -> "Almoço";
            case "DESLOCAMENTO" -> "Deslocamento";
            case "TREINO" -> "Treino";
            case "PLANTAO" -> "Plantão";
            default -> "Compromisso";
        };
    }

    private static String rotuloTabelaBloco(TipoBloco tipo) {
        String simples = rotuloSimplesBloco(tipo);
        return tipo == TipoBloco.PROFUNDO ? "**" + simples + "**" : simples;
    }

    private static String rotuloSimplesBloco(TipoBloco tipo) {
        return switch (tipo) {
            case PROFUNDO -> "Bloco profundo";
            case INTERROMPIVEL -> "Bloco interrompível";
            case VIGILANCIA -> "Bloco de vigilância";
        };
    }

    private static String nomeDiaPorExtenso(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "Segunda-feira";
            case TUESDAY -> "Terça-feira";
            case WEDNESDAY -> "Quarta-feira";
            case THURSDAY -> "Quinta-feira";
            case FRIDAY -> "Sexta-feira";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    private static String abreviacaoDia(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "seg";
            case TUESDAY -> "ter";
            case WEDNESDAY -> "qua";
            case THURSDAY -> "qui";
            case FRIDAY -> "sex";
            case SATURDAY -> "sáb";
            case SUNDAY -> "dom";
        };
    }

    private static String sanitizarLinha(String texto) {
        return texto.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String sanitizarCelula(String texto) {
        return sanitizarLinha(texto).replace("|", "\\|");
    }

    private static boolean temTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    private record LinhaDia(
            long inicioNanos,
            long fimNanos,
            int ordemCategoria,
            String horario,
            String tipo,
            String detalhe) {
    }
}
