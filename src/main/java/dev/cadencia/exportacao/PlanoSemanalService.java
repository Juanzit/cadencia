package dev.cadencia.exportacao;

import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.dominio.Semana;
import dev.cadencia.exportacao.strategies.ExportadorStrategy;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por orquestrar a geração da semana, a formatação via
 * {@link ExportadorStrategy} e a gravação em {@code <diretorioDados>/semana/AAAA-Wnn.md}.
 */
public class PlanoSemanalService {

    private static final String ARQUIVO_AGENDA_PADRAO = "agenda.yaml";
    private static final String SUBDIRETORIO_SEMANA = "semana";

    private final AgendaService agendaService;
    private final ExportadorStrategy exportadorStrategy;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final Path diretorioDados;

    public PlanoSemanalService(
            AgendaService agendaService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            Path diretorioDados) {
        this.agendaService = Objects.requireNonNull(agendaService, "agendaService não pode ser nulo");
        this.exportadorStrategy = Objects.requireNonNull(
                exportadorStrategy, "exportadorStrategy não pode ser nula");
        this.planoSemanalRepository = Objects.requireNonNull(
                planoSemanalRepository, "planoSemanalRepository não pode ser nulo");
        Objects.requireNonNull(diretorioDados, "diretorioDados não pode ser nulo");
        if (diretorioDados.toString().isBlank()) {
            throw new IllegalArgumentException("diretorioDados não pode ser vazio");
        }
        this.diretorioDados = diretorioDados.normalize();
    }

    /**
     * Gera e persiste o plano semanal Markdown. Quando {@code caminhoAgenda} é {@code null}, usa
     * {@code <diretorioDados>/agenda.yaml}; quando {@code dataReferencia} é {@code null}, delega
     * para {@link AgendaService#gerarSemana(Path)} preservando o {@code Clock} injetado.
     */
    public PlanoSemanalGerado gerarEExportar(Path caminhoAgenda, LocalDate dataReferencia) {
        Path agendaEfetiva = caminhoAgenda != null
                ? caminhoAgenda
                : diretorioDados.resolve(ARQUIVO_AGENDA_PADRAO);

        SemanaPlanejada semanaPlanejada = dataReferencia != null
                ? agendaService.gerarSemana(agendaEfetiva, dataReferencia)
                : agendaService.gerarSemana(agendaEfetiva);

        String conteudoMarkdown = exportadorStrategy.exportar(semanaPlanejada);
        Path destino = resolverArquivoSaida(semanaPlanejada.semana());
        Path arquivoSalvo = planoSemanalRepository.salvar(destino, conteudoMarkdown);
        return new PlanoSemanalGerado(arquivoSalvo, semanaPlanejada);
    }

    private Path resolverArquivoSaida(Semana semana) {
        String nomeArquivo = String.format(Locale.ROOT, "%04d-W%02d.md", semana.ano(), semana.numeroIso());
        return diretorioDados.resolve(SUBDIRETORIO_SEMANA).resolve(nomeArquivo);
    }
}
