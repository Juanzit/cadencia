package dev.cadencia.planejamento;

import dev.cadencia.dominio.Pedaco;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável produzido por uma estratégia de alocação de tarefas em blocos disponíveis.
 */
public record ResultadoAlocacao(
        List<PedacoAlocado> alocados,
        List<Pedaco> naoAlocados) {

    public ResultadoAlocacao {
        Objects.requireNonNull(alocados, "alocados não pode ser nulo");
        Objects.requireNonNull(naoAlocados, "naoAlocados não pode ser nulo");
        alocados = List.copyOf(alocados);
        naoAlocados = List.copyOf(naoAlocados);
    }
}
