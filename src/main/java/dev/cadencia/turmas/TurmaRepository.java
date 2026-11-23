package dev.cadencia.turmas;

import dev.cadencia.dominio.Turma;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Porta de leitura do cadastro de turmas estruturado em {@link Turma}s de domínio.
 */
public interface TurmaRepository {

    /**
     * Verifica se já existe um arquivo de turmas no caminho informado.
     */
    boolean existe(Path caminho);

    /**
     * Carrega e valida as turmas do arquivo CSV informado, preservando a ordem declarada e
     * reportando arquivo e número da linha em caso de falha (adotando {@code linha 1} para
     * arquivos ausentes, ilegíveis ou vazios).
     */
    List<Turma> carregar(Path caminho);

    /**
     * Carrega as turmas caso o arquivo exista; caso contrário, retorna {@link Optional#empty()}.
     */
    Optional<List<Turma>> carregarSeExistir(Path caminho);
}
