package dev.cadencia.planejamento;

import dev.cadencia.dominio.Tarefa;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Porta de leitura do backlog de estudos estruturado em {@link Tarefa}s de domínio.
 */
public interface BacklogRepository {

    /**
     * Carrega e valida as tarefas do arquivo de backlog informado, preservando a ordem declarada.
     */
    List<Tarefa> carregar(Path caminho);

    /**
     * Carrega o backlog caso o arquivo exista; caso contrário, retorna {@link Optional#empty()}.
     */
    Optional<List<Tarefa>> carregarSeExistir(Path caminho);
}
