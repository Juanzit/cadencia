package dev.cadencia.planejamento;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.cadencia.dominio.Tarefa;
import dev.cadencia.dominio.TipoBloco;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DTO de entrada para desserialização e validação via Bean Validation do arquivo {@code backlog.yaml}.
 */
@BacklogDto.IdsDeTarefasUnicos
public record BacklogDto(
        @NotNull(message = "seção 'tarefas' é obrigatória")
        @Valid
        @JsonProperty("tarefas")
        List<@NotNull(message = "tarefa não pode ser nula") @Valid TarefaDto> tarefas) {

    static final String REGEX_TIPO_BLOCO = "^(PROFUNDO|INTERROMPIVEL|VIGILANCIA)$";

    /**
     * Converte o DTO já validado em uma lista imutável de {@link Tarefa}s preservando a ordem do
     * roadmap e adotando {@link Tarefa#TAMANHO_PEDACO_PADRAO} quando {@code tamanho_pedaco_min} é nulo.
     */
    public List<Tarefa> paraDominio() {
        return paraDominio(Tarefa.TAMANHO_PEDACO_PADRAO);
    }

    /**
     * Converte o DTO já validado em uma lista imutável de {@link Tarefa}s preservando a ordem do
     * roadmap e aplicando {@code tamanhoPedacoPadrao} quando {@code tamanho_pedaco_min} não é informado.
     */
    public List<Tarefa> paraDominio(Duration tamanhoPedacoPadrao) {
        java.util.Objects.requireNonNull(tamanhoPedacoPadrao, "tamanhoPedacoPadrao não pode ser nulo");
        return tarefas.stream()
                .map(tarefa -> tarefa.paraDominio(tamanhoPedacoPadrao))
                .toList();
    }

    public record TarefaDto(
            @NotBlank(message = "id é obrigatório")
            @JsonProperty("id")
            String id,

            @NotBlank(message = "titulo é obrigatório")
            @JsonProperty("titulo")
            String titulo,

            @NotNull(message = "duracao_total_min é obrigatório")
            @Positive(message = "duracao_total_min deve ser maior que zero")
            @JsonProperty("duracao_total_min")
            Integer duracaoTotalMin,

            @NotNull(message = "tipos_aceitos é obrigatório")
            @NotEmpty(message = "tipos_aceitos deve conter ao menos um tipo de bloco")
            @JsonProperty("tipos_aceitos")
            List<
                    @NotNull(message = "tipo de bloco aceito não pode ser nulo")
                    @Pattern(
                            regexp = REGEX_TIPO_BLOCO,
                            message = "tipo inválido. Use PROFUNDO, INTERROMPIVEL ou VIGILANCIA")
                    String> tiposAceitos,

            @Positive(message = "tamanho_pedaco_min deve ser maior que zero")
            @JsonProperty("tamanho_pedaco_min")
            Integer tamanhoPedacoMin,

            @NotNull(message = "prioridade é obrigatória")
            @Positive(message = "prioridade deve ser maior que zero")
            @JsonProperty("prioridade")
            Integer prioridade) {

        Tarefa paraDominio(Duration tamanhoPedacoPadrao) {
            List<TipoBloco> tipos = tiposAceitos.stream()
                    .map(TipoBloco::valueOf)
                    .toList();
            Duration tamanhoPedaco = tamanhoPedacoMin != null
                    ? Duration.ofMinutes(tamanhoPedacoMin)
                    : tamanhoPedacoPadrao;
            return new Tarefa(
                    id.trim(),
                    titulo.trim(),
                    Duration.ofMinutes(duracaoTotalMin),
                    tipos,
                    tamanhoPedaco,
                    prioridade);
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = IdsDeTarefasUnicosValidator.class)
    @interface IdsDeTarefasUnicos {
        String message() default "ids das tarefas não podem ser duplicados";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Garante que todas as tarefas possuam {@code id} único, reportando a violação diretamente em
     * {@code tarefas[i].id}.
     */
    public static class IdsDeTarefasUnicosValidator
            implements ConstraintValidator<IdsDeTarefasUnicos, BacklogDto> {

        @Override
        public boolean isValid(BacklogDto backlog, ConstraintValidatorContext contexto) {
            if (backlog == null || backlog.tarefas() == null || backlog.tarefas().size() < 2) {
                return true;
            }

            Set<String> idsVistos = new HashSet<>();
            boolean valido = true;
            for (int i = 0; i < backlog.tarefas().size(); i++) {
                TarefaDto tarefa = backlog.tarefas().get(i);
                if (tarefa == null || tarefa.id() == null || tarefa.id().isBlank()) {
                    continue;
                }
                String idNormalizado = tarefa.id().trim();
                if (!idsVistos.add(idNormalizado)) {
                    if (valido) {
                        contexto.disableDefaultConstraintViolation();
                        valido = false;
                    }
                    String mensagem = String.format(
                            "id \"%s\" duplicado; cada tarefa deve possuir um identificador único",
                            idNormalizado);
                    // No Jakarta Bean Validation, inIterable() aplica o índice ao nó pai ('tarefas')
                    // imediatamente antes do nó corrente ('id'), produzindo "tarefas[i].id".
                    contexto.buildConstraintViolationWithTemplate(mensagem)
                            .addPropertyNode("tarefas")
                            .addPropertyNode("id")
                            .inIterable()
                            .atIndex(i)
                            .addConstraintViolation();
                }
            }
            return valido;
        }
    }
}
