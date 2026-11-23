package dev.cadencia.turmas;

import dev.cadencia.dominio.Turma;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * DTO de entrada por linha para leitura e validação via Bean Validation do arquivo {@code turmas.csv}.
 *
 * @param turma          código identificador da turma (coluna {@code turma})
 * @param empresa        empresa ou cliente associado (coluna {@code empresa})
 * @param moduloAtual    nome ou identificador do módulo atual (coluna {@code modulo_atual})
 * @param inicioModulo   data de início do módulo (coluna {@code inicio_modulo})
 * @param duracaoSemanas duração prevista do módulo em semanas (coluna {@code duracao_semanas})
 */
public record TurmaDto(
        @NotBlank(message = "turma é obrigatória")
        String turma,

        @NotBlank(message = "empresa é obrigatória")
        String empresa,

        @NotBlank(message = "modulo_atual é obrigatório")
        String moduloAtual,

        @NotNull(message = "inicio_modulo é obrigatório")
        LocalDate inicioModulo,

        @NotNull(message = "duracao_semanas é obrigatória")
        @Positive(message = "duracao_semanas deve ser maior que zero")
        Integer duracaoSemanas) {

    /**
     * Converte o DTO já validado no modelo imutável de domínio {@link Turma}.
     *
     * @return instância de {@link Turma} correspondente à linha validada
     */
    public Turma paraDominio() {
        return new Turma(
                turma.trim(),
                empresa.trim(),
                moduloAtual.trim(),
                inicioModulo,
                duracaoSemanas);
    }
}
