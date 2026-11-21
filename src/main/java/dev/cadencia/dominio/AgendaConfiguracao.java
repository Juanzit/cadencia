package dev.cadencia.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Configuração completa e imutável da agenda semanal carregada para o domínio.
 */
public record AgendaConfiguracao(
        Jornada jornada,
        Margens margens,
        Classificacao classificacao,
        List<Compromisso> compromissos,
        List<BlocoManual> blocosManuais) {

    public AgendaConfiguracao {
        Objects.requireNonNull(jornada, "jornada não pode ser nula");
        Objects.requireNonNull(margens, "margens não pode ser nulo");
        Objects.requireNonNull(classificacao, "classificacao não pode ser nula");
        Objects.requireNonNull(compromissos, "compromissos não pode ser nulo");
        Objects.requireNonNull(blocosManuais, "blocosManuais não pode ser nulo");
        compromissos = List.copyOf(compromissos);
        blocosManuais = List.copyOf(blocosManuais);
    }
}
