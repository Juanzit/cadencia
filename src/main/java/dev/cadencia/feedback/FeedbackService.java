package dev.cadencia.feedback;

import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.Pedaco;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.StatusBloco;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PedacoAlocado;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por gerar o modelo de feedback semanal em
 * {@code <diretorioDados>/feedback/AAAA-Wnn.md}, reutilizando a semana planejada e a alocação
 * opcional de estudos sem sobrescrever arquivos existentes a menos que {@code sobrescrever} seja
 * informado.
 */
public class FeedbackService {

    private static final String ARQUIVO_AGENDA_PADRAO = "agenda.yaml";
    private static final String ARQUIVO_BACKLOG_PADRAO = "backlog.yaml";
    private static final String SUBDIRETORIO_FEEDBACK = "feedback";

    private static final Comparator<PedacoAlocado> ORDEM_ALOCADOS = Comparator
            .comparing(PedacoAlocado::data)
            .thenComparing(PedacoAlocado::inicio)
            .thenComparing(PedacoAlocado::fim);

    private static final Comparator<Bloco> ORDEM_BLOCOS = Comparator
            .comparing(Bloco::data)
            .thenComparing(Bloco::inicio)
            .thenComparing(Bloco::fim)
            .thenComparing(Bloco::tipo)
            .thenComparing(Bloco::descricao, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<ItemFeedback> ORDEM_ITENS_FEEDBACK = Comparator
            .comparingInt((ItemFeedback item) -> item.diaSemana().getValue())
            .thenComparing(ItemFeedback::inicio)
            .thenComparing(ItemFeedback::fim, Comparator.nullsFirst(LocalTime::compareTo));

    private final AgendaService agendaService;
    private final BacklogRepository backlogRepository;
    private final PlanejamentoService planejamentoService;
    private final FeedbackRepository feedbackRepository;
    private final ConfiabilidadeService confiabilidadeService;
    private final Path diretorioDados;

    public FeedbackService(
            AgendaService agendaService,
            FeedbackRepository feedbackRepository,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                null,
                null,
                Objects.requireNonNull(feedbackRepository, "feedbackRepository não pode ser nulo"),
                null);
    }

    public FeedbackService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackRepository feedbackRepository,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                Objects.requireNonNull(backlogRepository, "backlogRepository não pode ser nulo"),
                Objects.requireNonNull(planejamentoService, "planejamentoService não pode ser nulo"),
                Objects.requireNonNull(feedbackRepository, "feedbackRepository não pode ser nulo"),
                null);
    }

    public FeedbackService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackRepository feedbackRepository,
            ConfiabilidadeService confiabilidadeService,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                Objects.requireNonNull(backlogRepository, "backlogRepository não pode ser nulo"),
                Objects.requireNonNull(planejamentoService, "planejamentoService não pode ser nulo"),
                Objects.requireNonNull(feedbackRepository, "feedbackRepository não pode ser nulo"),
                Objects.requireNonNull(confiabilidadeService, "confiabilidadeService não pode ser nulo"));
    }

    private FeedbackService(
            Path diretorioDadosNormalizado,
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackRepository feedbackRepository,
            ConfiabilidadeService confiabilidadeService) {
        this.diretorioDados = diretorioDadosNormalizado;
        this.agendaService = agendaService;
        this.backlogRepository = backlogRepository;
        this.planejamentoService = planejamentoService;
        this.feedbackRepository = feedbackRepository;
        this.confiabilidadeService = confiabilidadeService;
    }

    /**
     * Gera e persiste o modelo de feedback semanal utilizando o backlog padrão caso exista em
     * {@code <diretorioDados>/backlog.yaml}.
     */
    public ModeloFeedbackGerado gerarModelo(
            Path caminhoAgenda, LocalDate dataReferencia, boolean sobrescrever) {
        return gerarModelo(caminhoAgenda, null, dataReferencia, sobrescrever);
    }

    /**
     * Gera e persiste {@code <diretorioDados>/feedback/AAAA-Wnn.md} preenchido com os estudos
     * alocados da semana ou, na ausência de alocações, com os blocos livres da semana.
     */
    public ModeloFeedbackGerado gerarModelo(
            Path caminhoAgenda,
            Path caminhoBacklog,
            LocalDate dataReferencia,
            boolean sobrescrever) {
        Path agendaEfetiva = caminhoAgenda != null
                ? caminhoAgenda
                : diretorioDados.resolve(ARQUIVO_AGENDA_PADRAO);

        SemanaPlanejada semanaPlanejada = dataReferencia != null
                ? agendaService.gerarSemana(agendaEfetiva, dataReferencia)
                : agendaService.gerarSemana(agendaEfetiva);

        List<Tarefa> tarefasBacklog = carregarBacklogOpcionalOuExplicito(caminhoBacklog);
        ResultadoAlocacao resultadoAlocacao = alocarSeHouverBacklog(semanaPlanejada, tarefasBacklog);

        Path destino = resolverArquivoFeedback(semanaPlanejada.semana());
        if (!sobrescrever && feedbackRepository.existe(destino)) {
            throw new DadosInvalidosException(String.format(
                    "O arquivo de feedback %s já existe.%n"
                    + "Execute novamente com --sobrescrever caso queira recriar o modelo.",
                    destino));
        }

        List<ItemFeedback> itens = construirItensModelo(semanaPlanejada, resultadoAlocacao);
        FeedbackSemanal modelo = new FeedbackSemanal(
                semanaPlanejada.semana().ano(),
                semanaPlanejada.semana().numeroIso(),
                itens,
                List.of());
        Path arquivoSalvo = feedbackRepository.salvar(destino, modelo);
        return new ModeloFeedbackGerado(arquivoSalvo, modelo, semanaPlanejada, resultadoAlocacao);
    }

    private ResultadoAlocacao alocarSeHouverBacklog(
            SemanaPlanejada semanaPlanejada, List<Tarefa> tarefasBacklog) {
        if (tarefasBacklog == null) {
            return null;
        }
        if (confiabilidadeService != null) {
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidades =
                    confiabilidadeService.calcularPorFaixa(semanaPlanejada.semana());
            return planejamentoService.alocar(semanaPlanejada, tarefasBacklog, confiabilidades);
        }
        return planejamentoService.alocar(semanaPlanejada, tarefasBacklog);
    }

    /**
     * Resolve, a partir dos feedbacks gravados em {@code <diretorioDados>/feedback}, o mapa
     * imutável da última nota {@code "parei aqui"} vigente por título normalizado para a semana
     * alvo (considerando exclusivamente semanas estritamente anteriores).
     */
    public Map<String, String> resolverNotasRetomada(Semana semanaAlvo) {
        Objects.requireNonNull(semanaAlvo, "semanaAlvo não pode ser nula");
        List<FeedbackSemanal> historico = feedbackRepository.listarHistorico(
                diretorioDados.resolve(SUBDIRETORIO_FEEDBACK));
        return resolverNotasRetomada(semanaAlvo, historico);
    }

    /**
     * Consolida de forma determinística a última nota {@code "parei aqui"} vigente por tarefa sem
     * mutar {@code historico}, considerando somente semanas estritamente anteriores à
     * {@code semanaAlvo}, ordenando semanas por {@code (ano, numeroIso)} e itens por
     * {@code (diaSemana, inicio, fim)}, dando precedência absoluta ao status {@code CONCLUIDO} e
     * preservando notas anteriores quando não houver nova nota nem conclusão.
     */
    public Map<String, String> resolverNotasRetomada(
            Semana semanaAlvo, List<FeedbackSemanal> historico) {
        Objects.requireNonNull(semanaAlvo, "semanaAlvo não pode ser nula");
        Objects.requireNonNull(historico, "historico não pode ser nulo");

        List<FeedbackSemanal> copiaHistorico = List.copyOf(historico);
        List<FeedbackSemanal> semanasAnteriores = copiaHistorico.stream()
                .filter(feedback -> feedback.ehAnteriorA(semanaAlvo))
                .sorted(FeedbackSemanal.ORDEM_CRONOLOGICA)
                .toList();

        Map<String, String> notasVigentes = new LinkedHashMap<>();
        for (FeedbackSemanal semana : semanasAnteriores) {
            List<ItemFeedback> itensOrdenados = new ArrayList<>(semana.itens());
            itensOrdenados.sort(ORDEM_ITENS_FEEDBACK);
            for (ItemFeedback item : itensOrdenados) {
                String chave = Tarefa.normalizarTitulo(item.titulo());
                if (item.status() == StatusBloco.CONCLUIDO) {
                    notasVigentes.remove(chave);
                } else if (item.possuiNotaRetomada()) {
                    notasVigentes.put(chave, item.notaRetomada());
                }
            }
        }
        return Collections.unmodifiableMap(notasVigentes);
    }

    private List<Tarefa> carregarBacklogOpcionalOuExplicito(Path caminhoBacklog) {
        if (caminhoBacklog != null) {
            if (backlogRepository == null || planejamentoService == null) {
                throw new IllegalStateException(
                        "BacklogRepository e PlanejamentoService são obrigatórios quando --backlog é informado.");
            }
            return backlogRepository.carregar(caminhoBacklog);
        }
        if (backlogRepository == null || planejamentoService == null) {
            return null;
        }
        return backlogRepository.carregarSeExistir(diretorioDados.resolve(ARQUIVO_BACKLOG_PADRAO))
                .orElse(null);
    }

    private Path resolverArquivoFeedback(Semana semana) {
        String nomeArquivo = String.format(Locale.ROOT, "%04d-W%02d.md", semana.ano(), semana.numeroIso());
        return diretorioDados.resolve(SUBDIRETORIO_FEEDBACK).resolve(nomeArquivo);
    }

    private static List<ItemFeedback> construirItensModelo(
            SemanaPlanejada semanaPlanejada, ResultadoAlocacao resultadoAlocacao) {
        if (resultadoAlocacao != null && !resultadoAlocacao.alocados().isEmpty()) {
            List<PedacoAlocado> alocados = new ArrayList<>(resultadoAlocacao.alocados());
            alocados.sort(ORDEM_ALOCADOS);
            List<ItemFeedback> itens = new ArrayList<>(alocados.size());
            for (PedacoAlocado alocado : alocados) {
                Pedaco pedaco = alocado.pedaco();
                String titulo = pedaco.tarefa().titulo().trim()
                        + " (" + pedaco.numero() + "/" + pedaco.total() + ")";
                itens.add(new ItemFeedback(
                        alocado.data().getDayOfWeek(),
                        alocado.inicio(),
                        alocado.fim(),
                        titulo,
                        StatusBloco.NAO_ACONTECEU,
                        null));
            }
            return itens;
        }

        List<Bloco> blocos = new ArrayList<>(semanaPlanejada.semana().blocos());
        blocos.sort(ORDEM_BLOCOS);
        List<ItemFeedback> itens = new ArrayList<>(blocos.size());
        for (Bloco bloco : blocos) {
            itens.add(new ItemFeedback(
                    bloco.data().getDayOfWeek(),
                    bloco.inicio(),
                    bloco.fim(),
                    rotuloBloco(bloco),
                    StatusBloco.NAO_ACONTECEU,
                    null));
        }
        return itens;
    }

    private static String rotuloBloco(Bloco bloco) {
        if (bloco.descricao() != null && !bloco.descricao().isBlank()) {
            return bloco.descricao().replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
        }
        Duration duracao = Duration.between(bloco.inicio(), bloco.fim());
        long minutosTotais = duracao.toMinutes();
        String rotulo = switch (bloco.tipo()) {
            case PROFUNDO -> "Bloco profundo";
            case INTERROMPIVEL -> "Bloco interrompível";
            case VIGILANCIA -> "Bloco de vigilância";
        };
        return String.format(Locale.ROOT, "%s (%dh%02d)", rotulo, minutosTotais / 60, minutosTotais % 60);
    }

    private static Path validarENormalizarDiretorio(Path diretorioDados) {
        Objects.requireNonNull(diretorioDados, "diretorioDados não pode ser nulo");
        if (diretorioDados.toString().isBlank()) {
            throw new IllegalArgumentException("diretorioDados não pode ser vazio");
        }
        return diretorioDados.normalize();
    }
}
