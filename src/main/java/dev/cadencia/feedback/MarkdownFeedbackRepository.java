package dev.cadencia.feedback;

import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.StatusBloco;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Adapter Markdown responsável pela leitura validada, listagem histórica ordenada e gravação
 * determinística e idempotente de arquivos {@code feedback/AAAA-Wnn.md} no formato do
 * {@code dev/DESIGN.md}.
 */
public class MarkdownFeedbackRepository implements FeedbackRepository {

    private static final String LF = "\n";
    private static final String BOM_UTF8 = "\uFEFF";
    private static final String SECAO_BLOCOS_E_ESTUDOS = "## Blocos e estudos";
    private static final String SECAO_NOTAS_LIVRES = "## Notas livres";
    private static final String PREFIXO_NOTA = "- ";
    private static final String PLACEHOLDER_NENHUMA_NOTA = "- Nenhuma nota registrada.";
    private static final int ANO_MINIMO = 2000;
    private static final int SEMANA_ISO_MINIMA = 1;
    private static final int SEMANA_ISO_MAXIMA = 53;

    private static final Pattern NOME_ARQUIVO_FEEDBACK_PATTERN =
            Pattern.compile("\\d{4}-W\\d{2}\\.md");
    private static final Pattern CABECALHO_PATTERN =
            Pattern.compile("^#\\s+Feedback\\s+(\\d{4})-W(\\d{2})\\s*$");
    private static final Pattern ITEM_CHECKBOX_PATTERN =
            Pattern.compile("^-\\s+\\[([^\\]]*)\\](?:\\s+(.*))?$");
    private static final Pattern DIA_HORARIO_RESTO_PATTERN =
            Pattern.compile("^(\\S+)\\s+(.+)$");
    private static final Pattern INTERVALO_PATTERN =
            Pattern.compile("^([^\\s–-]+)[–-]([^\\s]+)(?:\\s+(.*))?$");
    private static final Pattern INTERVALO_ESPACADO_PATTERN =
            Pattern.compile("^[–-]\\s+(\\d{1,2}:\\d{2})(?:\\s+(.*))?$");
    private static final Pattern HORA_STRICT_PATTERN = Pattern.compile("\\d{2}:\\d{2}");
    private static final Pattern PIPE_NAO_ESCAPADO_PATTERN = Pattern.compile("(?<!\\\\)\\|");

    private static final DateTimeFormatter FORMATADOR_HORA =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static final Map<String, DayOfWeek> DIAS_POR_ABREVIACAO = Map.of(
            "seg", DayOfWeek.MONDAY,
            "ter", DayOfWeek.TUESDAY,
            "qua", DayOfWeek.WEDNESDAY,
            "qui", DayOfWeek.THURSDAY,
            "sex", DayOfWeek.FRIDAY,
            "sáb", DayOfWeek.SATURDAY,
            "sab", DayOfWeek.SATURDAY,
            "dom", DayOfWeek.SUNDAY);

    @Override
    public boolean existe(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        return Files.exists(caminho);
    }

    @Override
    public FeedbackSemanal carregar(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        List<String> linhas = lerLinhasUtf8(caminho);
        return parsearDocumento(caminho, linhas);
    }

    @Override
    public Optional<FeedbackSemanal> carregarSeExistir(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        if (!Files.exists(caminho)) {
            return Optional.empty();
        }
        return Optional.of(carregar(caminho));
    }

    @Override
    public List<FeedbackSemanal> listarHistorico(Path diretorioFeedback) {
        Objects.requireNonNull(diretorioFeedback, "diretorioFeedback não pode ser nulo");
        if (!Files.exists(diretorioFeedback)) {
            return List.of();
        }
        if (!Files.isDirectory(diretorioFeedback)) {
            throw new DadosInvalidosException(
                    "Não foi possível listar feedbacks em " + diretorioFeedback
                            + ": o caminho informado não é um diretório.");
        }

        List<Path> arquivos;
        try (Stream<Path> entradas = Files.list(diretorioFeedback)) {
            arquivos = entradas
                    .filter(Files::isRegularFile)
                    .filter(p -> NOME_ARQUIVO_FEEDBACK_PATTERN
                            .matcher(p.getFileName().toString())
                            .matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new DadosInvalidosException(
                    "Não foi possível listar os arquivos de feedback em " + diretorioFeedback + ".", e);
        }

        return arquivos.stream()
                .map(this::carregar)
                .sorted(Comparator
                        .comparingInt(FeedbackSemanal::ano)
                        .thenComparingInt(FeedbackSemanal::numeroIso))
                .toList();
    }

    @Override
    public Path salvar(Path destino, FeedbackSemanal feedback) {
        Objects.requireNonNull(destino, "destino não pode ser nulo");
        Objects.requireNonNull(feedback, "feedback não pode ser nulo");

        String conteudo = formatarMarkdown(feedback);
        try {
            Path diretorioPai = destino.getParent();
            if (diretorioPai != null) {
                Files.createDirectories(diretorioPai);
            }
            return Files.writeString(
                    destino,
                    conteudo,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Erro ao gravar feedback semanal em " + destino, ex);
        }
    }

    private static List<String> lerLinhasUtf8(Path caminho) {
        try {
            return Files.readAllLines(caminho, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DadosInvalidosException(formatarErroLeitura(caminho), e);
        }
    }

    private static FeedbackSemanal parsearDocumento(Path caminho, List<String> linhas) {
        Integer ano = null;
        Integer numeroIso = null;
        SecaoAtual secao = SecaoAtual.ANTES_SECAO;
        boolean viuBlocosEStudos = false;
        boolean viuNotasLivres = false;
        List<ItemFeedback> itens = new ArrayList<>();
        List<String> notasGerais = new ArrayList<>();

        for (int i = 0; i < linhas.size(); i++) {
            int numeroLinha = i + 1;
            String linha = limparLinhaInicial(i, linhas.get(i));
            if (linha.isEmpty()) {
                continue;
            }

            if (ano == null) {
                int[] cabecalho = parsearCabecalho(caminho, numeroLinha, linha);
                ano = cabecalho[0];
                numeroIso = cabecalho[1];
                continue;
            }

            if (linha.startsWith("#")) {
                if (linha.equals(SECAO_BLOCOS_E_ESTUDOS)) {
                    if (viuBlocosEStudos || viuNotasLivres || !itens.isEmpty()) {
                        throw new DadosInvalidosException(String.format(
                                "Erro em %s (linha %d): seção \"%s\" duplicada ou fora de ordem.",
                                caminho, numeroLinha, SECAO_BLOCOS_E_ESTUDOS));
                    }
                    secao = SecaoAtual.BLOCOS_E_ESTUDOS;
                    viuBlocosEStudos = true;
                } else if (linha.equals(SECAO_NOTAS_LIVRES)) {
                    if (viuNotasLivres) {
                        throw new DadosInvalidosException(String.format(
                                "Erro em %s (linha %d): seção \"%s\" duplicada.",
                                caminho, numeroLinha, SECAO_NOTAS_LIVRES));
                    }
                    secao = SecaoAtual.NOTAS_LIVRES;
                    viuNotasLivres = true;
                } else {
                    throw new DadosInvalidosException(String.format(
                            "Erro em %s (linha %d): seção \"%s\" desconhecida. Use \"%s\" ou \"%s\".",
                            caminho, numeroLinha, linha, SECAO_BLOCOS_E_ESTUDOS, SECAO_NOTAS_LIVRES));
                }
                continue;
            }

            if (secao == SecaoAtual.NOTAS_LIVRES) {
                parsearLinhaNotaLivre(linha).ifPresent(notasGerais::add);
            } else {
                itens.add(parsearItem(caminho, numeroLinha, linha));
            }
        }

        if (ano == null) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha 1): arquivo de feedback vazio. Informe o cabeçalho \"# Feedback AAAA-Wnn\".",
                    caminho));
        }
        return new FeedbackSemanal(ano, numeroIso, itens, notasGerais);
    }

    private static String limparLinhaInicial(int indiceLinha, String bruta) {
        String limpa = indiceLinha == 0 && bruta.startsWith(BOM_UTF8) ? bruta.substring(1) : bruta;
        return limpa.trim();
    }

    private static int[] parsearCabecalho(Path caminho, int numeroLinha, String linha) {
        Matcher matcher = CABECALHO_PATTERN.matcher(linha);
        if (!matcher.matches()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): cabeçalho inválido \"%s\". Esperado \"# Feedback AAAA-Wnn\" (ex.: \"# Feedback 2026-W48\").",
                    caminho, numeroLinha, linha));
        }
        int ano = Integer.parseInt(matcher.group(1));
        int semanaIso = Integer.parseInt(matcher.group(2));
        if (ano < ANO_MINIMO) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): ano %d inválido no cabeçalho. Informe ano >= %d.",
                    caminho, numeroLinha, ano, ANO_MINIMO));
        }
        if (semanaIso < SEMANA_ISO_MINIMA || semanaIso > SEMANA_ISO_MAXIMA) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): semana ISO W%02d inválida no cabeçalho. Informe valor entre W01 e W53.",
                    caminho, numeroLinha, semanaIso));
        }
        return new int[] {ano, semanaIso};
    }

    private static ItemFeedback parsearItem(Path caminho, int numeroLinha, String linha) {
        Matcher checkbox = ITEM_CHECKBOX_PATTERN.matcher(linha);
        if (!checkbox.matches()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): formato de item inválido. Use \"- [x] <dia> <horario> · <titulo> | CONCLUIDO\" ou \"- [ ] <dia> <horario> · <titulo> | INTERROMPIDO | <nota>\".",
                    caminho, numeroLinha));
        }

        boolean marcado = interpretarCheckbox(caminho, numeroLinha, checkbox.group(1));
        String conteudo = checkbox.group(2);
        if (conteudo == null || conteudo.isBlank()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): item de feedback vazio após o checkbox.",
                    caminho, numeroLinha));
        }

        String[] partes = PIPE_NAO_ESCAPADO_PATTERN.split(conteudo, -1);
        if (partes.length < 2) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): status ausente. Separe descrição e status com \"|\" usando CONCLUIDO, INTERROMPIDO ou NAO_ACONTECEU.",
                    caminho, numeroLinha));
        }
        if (partes.length > 3) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): separadores \"|\" em excesso. Use no máximo \"<item> | <STATUS> | <nota opcional>\".",
                    caminho, numeroLinha));
        }

        String esquerda = partes[0].trim();
        StatusBloco status = interpretarStatus(caminho, numeroLinha, partes[1].trim());
        validarConsistenciaCheckboxEStatus(caminho, numeroLinha, marcado, status);
        String notaRetomada = partes.length == 3 ? desescaparPipe(partes[2].trim()) : null;

        CabecalhoItem cabecalhoItem = interpretarCabecalhoItem(caminho, numeroLinha, esquerda);
        return new ItemFeedback(
                cabecalhoItem.diaSemana(),
                cabecalhoItem.inicio(),
                cabecalhoItem.fim(),
                cabecalhoItem.titulo(),
                status,
                notaRetomada);
    }

    private static boolean interpretarCheckbox(Path caminho, int numeroLinha, String marcador) {
        if ("x".equalsIgnoreCase(marcador)) {
            return true;
        }
        if (" ".equals(marcador)) {
            return false;
        }
        throw new DadosInvalidosException(String.format(
                "Erro em %s (linha %d): marcador \"[%s]\" inválido. Use \"[x]\" para CONCLUIDO ou \"[ ]\" para INTERROMPIDO e NAO_ACONTECEU.",
                caminho, numeroLinha, marcador));
    }

    private static StatusBloco interpretarStatus(Path caminho, int numeroLinha, String statusTexto) {
        return switch (statusTexto) {
            case "CONCLUIDO" -> StatusBloco.CONCLUIDO;
            case "INTERROMPIDO" -> StatusBloco.INTERROMPIDO;
            case "NAO_ACONTECEU" -> StatusBloco.NAO_ACONTECEU;
            default -> throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): status \"%s\" inválido. Use CONCLUIDO, INTERROMPIDO ou NAO_ACONTECEU.",
                    caminho, numeroLinha, statusTexto));
        };
    }

    private static void validarConsistenciaCheckboxEStatus(
            Path caminho, int numeroLinha, boolean marcado, StatusBloco status) {
        if (marcado && status != StatusBloco.CONCLUIDO) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): item marcado como [x] exige status CONCLUIDO, mas recebeu %s. Use [ ] para %s ou altere o status para CONCLUIDO.",
                    caminho, numeroLinha, status, status));
        }
        if (!marcado && status == StatusBloco.CONCLUIDO) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): status CONCLUIDO exige checkbox marcado como [x], mas está [ ].",
                    caminho, numeroLinha));
        }
    }

    private static CabecalhoItem interpretarCabecalhoItem(
            Path caminho, int numeroLinha, String esquerda) {
        Matcher diaEResto = DIA_HORARIO_RESTO_PATTERN.matcher(esquerda);
        if (!diaEResto.matches()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): informe dia, horário e título antes de \"|\". Ex.: \"seg 07:00–07:25 · Java Core (1/4)\".",
                    caminho, numeroLinha));
        }

        String diaToken = diaEResto.group(1);
        DayOfWeek diaSemana = DIAS_POR_ABREVIACAO.get(diaToken.toLowerCase(Locale.ROOT));
        if (diaSemana == null) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): dia da semana \"%s\" inválido. Use seg, ter, qua, qui, sex, sáb ou dom.",
                    caminho, numeroLinha, diaToken));
        }

        String aposDia = diaEResto.group(2).trim();
        String inicioTexto;
        String fimTexto = null;
        String resto;

        Matcher intervaloMatcher = INTERVALO_PATTERN.matcher(aposDia);
        if (intervaloMatcher.matches()) {
            inicioTexto = intervaloMatcher.group(1);
            fimTexto = intervaloMatcher.group(2);
            resto = intervaloMatcher.group(3);
        } else {
            String[] partesHorario = aposDia.split("\\s+", 2);
            inicioTexto = partesHorario[0];
            resto = partesHorario.length > 1 ? partesHorario[1] : null;
            if (resto != null) {
                Matcher intervaloEspacado = INTERVALO_ESPACADO_PATTERN.matcher(resto.trim());
                if (intervaloEspacado.matches()) {
                    fimTexto = intervaloEspacado.group(1);
                    resto = intervaloEspacado.group(2);
                }
            }
        }

        LocalTime inicio = parsearHorario(caminho, numeroLinha, "início", inicioTexto);
        LocalTime fim = fimTexto != null ? parsearHorario(caminho, numeroLinha, "fim", fimTexto) : null;
        if (fim != null && !fim.isAfter(inicio)) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): horário de fim (%s) deve ser estritamente posterior ao início (%s).",
                    caminho, numeroLinha, fimTexto, inicioTexto));
        }

        String titulo = extrairTitulo(caminho, numeroLinha, resto);
        return new CabecalhoItem(diaSemana, inicio, fim, titulo);
    }

    private static LocalTime parsearHorario(
            Path caminho, int numeroLinha, String papel, String valor) {
        if (!HORA_STRICT_PATTERN.matcher(valor).matches()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): horário de %s \"%s\" inválido. Use o formato HH:mm entre 00:00 e 23:59.",
                    caminho, numeroLinha, papel, valor));
        }
        try {
            return LocalTime.parse(valor, FORMATADOR_HORA);
        } catch (DateTimeParseException e) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): horário de %s \"%s\" inválido. Use o formato HH:mm entre 00:00 e 23:59.",
                    caminho, numeroLinha, papel, valor), e);
        }
    }

    private static String extrairTitulo(Path caminho, int numeroLinha, String resto) {
        if (resto == null || resto.isBlank()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): título do bloco/estudo ausente.",
                    caminho, numeroLinha));
        }
        String limpo = resto.trim();
        if (limpo.startsWith("·")) {
            limpo = limpo.substring(1).trim();
        }
        if (limpo.isEmpty()) {
            throw new DadosInvalidosException(String.format(
                    "Erro em %s (linha %d): título do bloco/estudo ausente.",
                    caminho, numeroLinha));
        }
        return desescaparPipe(limpo);
    }

    private static String formatarMarkdown(FeedbackSemanal feedback) {
        StringBuilder md = new StringBuilder();
        md.append(String.format(Locale.ROOT, "# Feedback %04d-W%02d", feedback.ano(), feedback.numeroIso()))
                .append(LF)
                .append(LF)
                .append(SECAO_BLOCOS_E_ESTUDOS)
                .append(LF);
        for (ItemFeedback item : feedback.itens()) {
            md.append(formatarItem(item)).append(LF);
        }

        md.append(LF).append(SECAO_NOTAS_LIVRES).append(LF);
        escreverNotasLivres(md, feedback.notasGerais());
        return md.toString();
    }

    private static Optional<String> parsearLinhaNotaLivre(String linha) {
        if (PLACEHOLDER_NENHUMA_NOTA.equals(linha)) {
            return Optional.empty();
        }
        String extraida = linha.startsWith(PREFIXO_NOTA)
                ? linha.substring(PREFIXO_NOTA.length()).trim()
                : linha;
        return Optional.of(extraida);
    }

    private static void escreverNotasLivres(StringBuilder md, List<String> notasGerais) {
        List<String> notasValidas = new ArrayList<>();
        for (String nota : notasGerais) {
            String limpa = sanitizarLinha(nota);
            if (!limpa.isEmpty()) {
                notasValidas.add(limpa);
            }
        }
        if (notasValidas.isEmpty()) {
            md.append(PLACEHOLDER_NENHUMA_NOTA).append(LF);
            return;
        }
        for (String nota : notasValidas) {
            md.append(PREFIXO_NOTA).append(nota).append(LF);
        }
    }

    private static String formatarItem(ItemFeedback item) {
        String checkbox = item.status() == StatusBloco.CONCLUIDO ? "[x]" : "[ ]";
        String horario = item.fim() != null
                ? FORMATADOR_HORA.format(item.inicio()) + "–" + FORMATADOR_HORA.format(item.fim())
                : FORMATADOR_HORA.format(item.inicio());

        StringBuilder linha = new StringBuilder();
        linha.append("- ")
                .append(checkbox)
                .append(' ')
                .append(abreviacaoDia(item.diaSemana()))
                .append(' ')
                .append(horario)
                .append(" · ")
                .append(escaparPipe(sanitizarLinha(item.titulo())))
                .append(" | ")
                .append(item.status().name());
        if (item.possuiNotaRetomada()) {
            linha.append(" | ").append(escaparPipe(sanitizarLinha(item.notaRetomada())));
        }
        return linha.toString();
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

    private static String escaparPipe(String texto) {
        return texto.replace("|", "\\|");
    }

    private static String desescaparPipe(String texto) {
        return texto.replace("\\|", "|");
    }

    private static String formatarErroLeitura(Path caminho) {
        return "Não foi possível ler " + caminho + ":\n"
                + "arquivo inexistente ou sem permissão de leitura.\n"
                + "Verifique o caminho e as permissões do arquivo.";
    }

    private enum SecaoAtual {
        ANTES_SECAO,
        BLOCOS_E_ESTUDOS,
        NOTAS_LIVRES
    }

    private record CabecalhoItem(
            DayOfWeek diaSemana,
            LocalTime inicio,
            LocalTime fim,
            String titulo) {
    }
}
