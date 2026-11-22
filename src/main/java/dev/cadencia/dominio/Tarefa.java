package dev.cadencia.dominio;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tarefa de estudo do backlog com duração total, tipos de bloco compatíveis, tamanho de pedaço
 * retomável (25 minutos por padrão) e prioridade determinística.
 */
public record Tarefa(
        String id,
        String titulo,
        Duration duracaoTotal,
        List<TipoBloco> tiposAceitos,
        Duration tamanhoPedaco,
        int prioridade) {

    public static final Duration TAMANHO_PEDACO_PADRAO = Duration.ofMinutes(25);

    public Tarefa {
        Objects.requireNonNull(id, "id não pode ser nulo");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id não pode ser vazio");
        }
        Objects.requireNonNull(titulo, "titulo não pode ser nulo");
        if (titulo.isBlank()) {
            throw new IllegalArgumentException("titulo não pode ser vazio");
        }
        Objects.requireNonNull(duracaoTotal, "duracaoTotal não pode ser nula");
        if (duracaoTotal.isZero() || duracaoTotal.isNegative()) {
            throw new IllegalArgumentException("duracaoTotal deve ser estritamente positiva");
        }
        Objects.requireNonNull(tiposAceitos, "tiposAceitos não pode ser nulo");
        tiposAceitos = List.copyOf(tiposAceitos);
        if (tiposAceitos.isEmpty()) {
            throw new IllegalArgumentException("tiposAceitos deve conter ao menos um TipoBloco");
        }
        Objects.requireNonNull(tamanhoPedaco, "tamanhoPedaco não pode ser nulo");
        if (tamanhoPedaco.isZero() || tamanhoPedaco.isNegative()) {
            throw new IllegalArgumentException("tamanhoPedaco deve ser estritamente positivo");
        }
        if (prioridade <= 0) {
            throw new IllegalArgumentException("prioridade deve ser estritamente positiva");
        }
    }

    /**
     * Construtor de conveniência que adota {@link #TAMANHO_PEDACO_PADRAO} (25 minutos).
     */
    public Tarefa(
            String id,
            String titulo,
            Duration duracaoTotal,
            List<TipoBloco> tiposAceitos,
            int prioridade) {
        this(id, titulo, duracaoTotal, tiposAceitos, TAMANHO_PEDACO_PADRAO, prioridade);
    }

    /**
     * Divide {@code duracaoTotal} em {@code Pedaco}s sequenciais de até {@code tamanhoPedaco},
     * calculando a quantidade pelo teto da divisão e alocando somente o restante no último pedaço.
     */
    public List<Pedaco> dividirEmPedacos() {
        long totalNanos = duracaoTotal.toNanos();
        long pedacoNanos = tamanhoPedaco.toNanos();
        int totalPedacos = Math.toIntExact(Math.ceilDiv(totalNanos, pedacoNanos));

        List<Pedaco> pedacos = new ArrayList<>(totalPedacos);
        long restanteNanos = totalNanos;
        for (int numero = 1; numero <= totalPedacos; numero++) {
            long fatiaNanos = Math.min(pedacoNanos, restanteNanos);
            pedacos.add(new Pedaco(this, numero, totalPedacos, Duration.ofNanos(fatiaNanos)));
            restanteNanos -= fatiaNanos;
        }
        return List.copyOf(pedacos);
    }
}
