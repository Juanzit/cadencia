package dev.cadencia.exportacao;

import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.exportacao.strategies.ExportadorStrategy;
import dev.cadencia.feedback.FeedbackService;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por orquestrar a geração da semana, a alocação opcional de
 * backlog via {@link PlanejamentoService}, a formatação via {@link ExportadorStrategy} e a gravação
 * em {@code <diretorioDados>/semana/AAAA-Wnn.md}.
 */
public class PlanoSemanalService {

    private static final String ARQUIVO_AGENDA_PADRAO = "agenda.yaml";
    private static final String ARQUIVO_BACKLOG_PADRAO = "backlog.yaml";
    private static final String SUBDIRETORIO_SEMANA = "semana";

    private final AgendaService agendaService;
    private final BacklogRepository backlogRepository;
    private final PlanejamentoService planejamentoService;
    private final FeedbackService feedbackService;
    private final ExportadorStrategy exportadorStrategy;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final Path diretorioDados;

    public PlanoSemanalService(
            AgendaService agendaService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this.agendaService = Objects.requireNonNull(agendaService, "agendaService não pode ser nulo");
        this.backlogRepository = null;
        this.planejamentoService = null;
        this.feedbackService = null;
        this.exportadorStrategy = Objects.requireNonNull(
                exportadorStrategy, "exportadorStrategy não pode ser nula");
        this.planoSemanalRepository = Objects.requireNonNull(
                planoSemanalRepository, "planoSemanalRepository não pode ser nulo");
        this.diretorioDados = validarENormalizarDiretorio(diretorioDados);
    }

    public PlanoSemanalService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this.agendaService = Objects.requireNonNull(agendaService, "agendaService não pode ser nulo");
        this.backlogRepository = Objects.requireNonNull(
                backlogRepository, "backlogRepository não pode ser nulo");
        this.planejamentoService = Objects.requireNonNull(
                planejamentoService, "planejamentoService não pode ser nulo");
        this.feedbackService = null;
        this.exportadorStrategy = Objects.requireNonNull(
                exportadorStrategy, "exportadorStrategy não pode ser nula");
        this.planoSemanalRepository = Objects.requireNonNull(
                planoSemanalRepository, "planoSemanalRepository não pode ser nulo");
        this.diretorioDados = validarENormalizarDiretorio(diretorioDados);
    }

    public PlanoSemanalService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackService feedbackService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this.agendaService = Objects.requireNonNull(agendaService, "agendaService não pode ser nulo");
        this.backlogRepository = Objects.requireNonNull(
                backlogRepository, "backlogRepository não pode ser nulo");
        this.planejamentoService = Objects.requireNonNull(
                planejamentoService, "planejamentoService não pode ser nulo");
        this.feedbackService = Objects.requireNonNull(
                feedbackService, "feedbackService não pode ser nulo");
        this.exportadorStrategy = Objects.requireNonNull(
                exportadorStrategy, "exportadorStrategy não pode ser nula");
        this.planoSemanalRepository = Objects.requireNonNull(
                planoSemanalRepository, "planoSemanalRepository não pode ser nulo");
        this.diretorioDados = validarENormalizarDiretorio(diretorioDados);
    }

    /**
     * Gera e persiste o plano semanal Markdown utilizando o backlog padrão caso exista em
     * {@code <diretorioDados>/backlog.yaml}.
     */
    public PlanoSemanalGerado gerarEExportar(Path caminhoAgenda, LocalDate dataReferencia) {
        return gerarEExportar(caminhoAgenda, null, dataReferencia);
    }

    /**
     * Gera e persiste o plano semanal Markdown. Quando {@code caminhoAgenda} é {@code null}, usa
     * {@code <diretorioDados>/agenda.yaml}; quando {@code caminhoBacklog} é {@code null}, carrega
     * {@code <diretorioDados>/backlog.yaml} caso exista; quando {@code dataReferencia} é {@code null},
     * delega para {@link AgendaService#gerarSemana(Path)} preservando o {@code Clock} injetado.
     */
    public PlanoSemanalGerado gerarEExportar(
            Path caminhoAgenda, Path caminhoBacklog, LocalDate dataReferencia) {
        Path agendaEfetiva = caminhoAgenda != null
                ? caminhoAgenda
                : diretorioDados.resolve(ARQUIVO_AGENDA_PADRAO);

        SemanaPlanejada semanaPlanejada = dataReferencia != null
                ? agendaService.gerarSemana(agendaEfetiva, dataReferencia)
                : agendaService.gerarSemana(agendaEfetiva);

        List<Tarefa> tarefasBacklog = carregarBacklogOpcionalOuExplicito(caminhoBacklog);
        ResultadoAlocacao resultadoAlocacao = tarefasBacklog != null
                ? planejamentoService.alocar(semanaPlanejada, tarefasBacklog)
                : null;

        String conteudoMarkdown;
        if (resultadoAlocacao != null) {
            Map<String, String> notasRetomada = feedbackService != null
                    ? feedbackService.resolverNotasRetomada(semanaPlanejada.semana())
                    : Map.of();
            conteudoMarkdown = exportadorStrategy.exportar(
                    semanaPlanejada, resultadoAlocacao, notasRetomada);
        } else {
            conteudoMarkdown = exportadorStrategy.exportar(semanaPlanejada);
        }

        Path destino = resolverArquivoSaida(semanaPlanejada.semana());
        Path arquivoSalvo = planoSemanalRepository.salvar(destino, conteudoMarkdown);
        return new PlanoSemanalGerado(arquivoSalvo, semanaPlanejada, resultadoAlocacao);
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

    private Path resolverArquivoSaida(Semana semana) {
        String nomeArquivo = String.format(Locale.ROOT, "%04d-W%02d.md", semana.ano(), semana.numeroIso());
        return diretorioDados.resolve(SUBDIRETORIO_SEMANA).resolve(nomeArquivo);
    }

    private static Path validarENormalizarDiretorio(Path diretorioDados) {
        Objects.requireNonNull(diretorioDados, "diretorioDados não pode ser nulo");
        if (diretorioDados.toString().isBlank()) {
            throw new IllegalArgumentException("diretorioDados não pode ser vazio");
        }
        return diretorioDados.normalize();
    }
}
