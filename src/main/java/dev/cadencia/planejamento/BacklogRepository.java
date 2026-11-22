package dev.cadencia.planejamento;

import dev.cadencia.dominio.Tarefa;
import java.nio.file.Path;
import java.util.List;

/**
 * Porta de leitura do backlog de estudos estruturado em {@link Tarefa}s de domínio.
 */
public interface BacklogRepository {

    /**
     * Carrega e valida as tarefas do arquivo de backlog informado, preservando a ordem declarada.
     */
    List<Tarefa> carregar(Path caminho);
}
