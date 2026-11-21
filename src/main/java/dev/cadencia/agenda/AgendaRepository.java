package dev.cadencia.agenda;

import dev.cadencia.dominio.AgendaConfiguracao;
import java.nio.file.Path;

/**
 * Porta de leitura da configuração completa da agenda semanal.
 */
public interface AgendaRepository {

    AgendaConfiguracao carregar(Path caminho);
}
