package dev.cadencia.feedback;

import dev.cadencia.dominio.FeedbackSemanal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência para leitura, listagem histórica e gravação de feedback semanal em
 * {@code <diretorioDados>/feedback/AAAA-Wnn.md}.
 */
public interface FeedbackRepository {

    /**
     * Carrega e valida o feedback semanal do arquivo informado, lançando erro detalhado por linha
     * caso o arquivo esteja ausente, ilegível, vazio ou fora do formato esperado.
     */
    FeedbackSemanal carregar(Path caminho);

    /**
     * Carrega o feedback semanal caso o arquivo exista; caso contrário, retorna
     * {@link Optional#empty()}.
     */
    Optional<FeedbackSemanal> carregarSeExistir(Path caminho);

    /**
     * Lista em ordem cronológica crescente os feedbacks encontrados no diretório informado,
     * retornando lista vazia caso o diretório ainda não exista.
     */
    List<FeedbackSemanal> listarHistorico(Path diretorioFeedback);

    /**
     * Grava deterministicamente o feedback semanal em UTF-8 com quebras LF, criando diretórios
     * pais quando necessário e sobrescrevendo o destino de forma idempotente.
     */
    Path salvar(Path destino, FeedbackSemanal feedback);
}
