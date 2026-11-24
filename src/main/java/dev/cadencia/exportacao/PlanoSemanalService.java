package dev.cadencia.exportacao;

import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.exportacao.strategies.ExportadorStrategy;
import dev.cadencia.feedback.ConfiabilidadeService;
import dev.cadencia.feedback.FeedbackService;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.ResultadoAlocacao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por orquestrar a geração da semana, o cálculo opcional de
 * confiabilidade, a alocação de backlog via {@link PlanejamentoService}, a formatação via
 * {@link ExportadorStrategy} e a gravação em {@code <diretorioDados>/semana/AAAA-Wnn.md}.
 */
public class PlanoSemanalService {

    private static final String ARQUIVO_AGENDA_PADRAO = "agenda.yaml";
    private static final String ARQUIVO_BACKLOG_PADRAO = "backlog.yaml";
    private static final String SUBDIRETORIO_SEMANA = "semana";
    private static final Comparator<ConfiabilidadeFaixa> ORDEM_FAIXAS = Comparator
            .comparingInt((ConfiabilidadeFaixa cf) -> cf.faixa().diaSemana().getValue())
            .thenComparingInt(cf -> cf.faixa().periodo().ordinal());

    private final AgendaService agendaService;
    private final BacklogRepository backlogRepository;
    private final PlanejamentoService planejamentoService;
    private final FeedbackService feedbackService;
    private final ConfiabilidadeService confiabilidadeService;
    private final ExportadorStrategy exportadorStrategy;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final Path diretorioDados;

    public PlanoSemanalService(
            AgendaService agendaService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                null,
                null,
                null,
                null,
                Objects.requireNonNull(exportadorStrategy, "exportadorStrategy não pode ser nula"),
                Objects.requireNonNull(planoSemanalRepository, "planoSemanalRepository não pode ser nulo"));
    }

    public PlanoSemanalService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                Objects.requireNonNull(backlogRepository, "backlogRepository não pode ser nulo"),
                Objects.requireNonNull(planejamentoService, "planejamentoService não pode ser nulo"),
                null,
                null,
                Objects.requireNonNull(exportadorStrategy, "exportadorStrategy não pode ser nula"),
                Objects.requireNonNull(planoSemanalRepository, "planoSemanalRepository não pode ser nulo"));
    }

    public PlanoSemanalService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackService feedbackService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                Objects.requireNonNull(backlogRepository, "backlogRepository não pode ser nulo"),
                Objects.requireNonNull(planejamentoService, "planejamentoService não pode ser nulo"),
                Objects.requireNonNull(feedbackService, "feedbackService não pode ser nulo"),
                null,
                Objects.requireNonNull(exportadorStrategy, "exportadorStrategy não pode ser nula"),
                Objects.requireNonNull(planoSemanalRepository, "planoSemanalRepository não pode ser nulo"));
    }

    public PlanoSemanalService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackService feedbackService,
            ConfiabilidadeService confiabilidadeService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this(
                validarENormalizarDiretorio(diretorioDados),
                Objects.requireNonNull(agendaService, "agendaService não pode ser nulo"),
                Objects.requireNonNull(backlogRepository, "backlogRepository não pode ser nulo"),
                Objects.requireNonNull(planejamentoService, "planejamentoService não pode ser nulo"),
                Objects.requireNonNull(feedbackService, "feedbackService não pode ser nulo"),
                Objects.requireNonNull(confiabilidadeService, "confiabilidadeService não pode ser nulo"),
                Objects.requireNonNull(exportadorStrategy, "exportadorStrategy não pode ser nula"),
                Objects.requireNonNull(planoSemanalRepository, "planoSemanalRepository não pode ser nulo"));
    }

    private PlanoSemanalService(
            Path diretorioDadosNormalizado,
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackService feedbackService,
            ConfiabilidadeService confiabilidadeService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository) {
        this.diretorioDados = diretorioDadosNormalizado;
        this.agendaService = agendaService;
        this.backlogRepository = backlogRepository;
        this.planejamentoService = planejamentoService;
        this.feedbackService = feedbackService;
        this.confiabilidadeService = confiabilidadeService;
        this.exportadorStrategy = exportadorStrategy;
        this.planoSemanalRepository = planoSemanalRepository;
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
        ResultadoAlocacao resultadoAlocacao = null;
        Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidades = Map.of();
        if (tarefasBacklog != null) {
            if (confiabilidadeService != null) {
                confiabilidades = confiabilidadeService.calcularPorFaixa(
                        semanaPlanejada.semana());
                resultadoAlocacao = planejamentoService.alocar(
                        semanaPlanejada, tarefasBacklog, confiabilidades);
            } else {
                resultadoAlocacao = planejamentoService.alocar(semanaPlanejada, tarefasBacklog);
            }
        }

        String conteudoMarkdown;
        if (resultadoAlocacao != null) {
            Map<String, String> notasRetomada = feedbackService != null
                    ? feedbackService.resolverNotasRetomada(semanaPlanejada.semana())
                    : Map.of();
            List<ConfiabilidadeFaixa> faixasBaixaConfiabilidade =
                    filtrarFaixasProfundasComBaixaConfiabilidade(
                            semanaPlanejada, confiabilidades, confiabilidadeService);
            conteudoMarkdown = exportadorStrategy.exportar(
                    semanaPlanejada, resultadoAlocacao, notasRetomada, faixasBaixaConfiabilidade);
        } else {
            conteudoMarkdown = exportadorStrategy.exportar(semanaPlanejada);
        }

        Path destino = resolverArquivoSaida(semanaPlanejada.semana());
        Path arquivoSalvo = planoSemanalRepository.salvar(destino, conteudoMarkdown);
        return new PlanoSemanalGerado(arquivoSalvo, semanaPlanejada, resultadoAlocacao);
    }

    private static List<ConfiabilidadeFaixa> filtrarFaixasProfundasComBaixaConfiabilidade(
            SemanaPlanejada semanaPlanejada,
            Map<FaixaHorario, ConfiabilidadeFaixa> confiabilidades,
            ConfiabilidadeService confiabilidadeService) {
        if (confiabilidadeService == null || confiabilidades.isEmpty()) {
            return List.of();
        }
        Map<FaixaHorario, ConfiabilidadeFaixa> encontradas = new LinkedHashMap<>();
        for (Bloco bloco : semanaPlanejada.semana().blocos()) {
            if (bloco.tipo() != TipoBloco.PROFUNDO) {
                continue;
            }
            FaixaHorario faixa = FaixaHorario.de(bloco.data().getDayOfWeek(), bloco.inicio());
            ConfiabilidadeFaixa cf = confiabilidades.get(faixa);
            if (confiabilidadeService.ehBaixaConfiabilidade(cf)) {
                encontradas.putIfAbsent(faixa, cf);
            }
        }
        return encontradas.values().stream()
                .sorted(ORDEM_FAIXAS)
                .toList();
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
