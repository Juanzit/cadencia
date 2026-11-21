package dev.cadencia.agenda;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.BlocoManual;
import dev.cadencia.dominio.Classificacao;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.Jornada;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.Margens;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.dominio.TipoCompromisso;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO de entrada para desserialização e validação via Bean Validation do arquivo {@code agenda.yaml}.
 */
public record AgendaDto(
        @NotNull(message = "seção 'jornada' é obrigatória")
        @Valid
        @JsonProperty("jornada")
        JornadaDto jornada,

        @NotNull(message = "seção 'margens' é obrigatória")
        @Valid
        @JsonProperty("margens")
        MargensDto margens,

        @NotNull(message = "seção 'classificacao' é obrigatória")
        @Valid
        @JsonProperty("classificacao")
        ClassificacaoDto classificacao,

        @NotNull(message = "seção 'compromissos' é obrigatória")
        @Valid
        @JsonProperty("compromissos")
        List<@NotNull(message = "compromisso não pode ser nulo") @Valid CompromissoDto> compromissos,

        @Valid
        @JsonProperty("blocos_manuais")
        List<@NotNull(message = "bloco manual não pode ser nulo") @Valid BlocoManualDto> blocosManuais) {

    static final String REGEX_DIA = "^(SEGUNDA|TERCA|QUARTA|QUINTA|SEXTA|SABADO|DOMINGO)$";
    static final String REGEX_HORARIO = "^(?:[01]\\d|2[0-3]):[0-5]\\d$";
    static final String REGEX_TIPO_COMPROMISSO = "^(AULA|REUNIAO|ALMOCO|DESLOCAMENTO|TREINO|PLANTAO)$";
    static final String REGEX_TIPO_BLOCO = "^(PROFUNDO|INTERROMPIVEL|VIGILANCIA)$";

    private static final Map<String, DayOfWeek> DIAS_SEMANA = Map.of(
            "SEGUNDA", DayOfWeek.MONDAY,
            "TERCA", DayOfWeek.TUESDAY,
            "QUARTA", DayOfWeek.WEDNESDAY,
            "QUINTA", DayOfWeek.THURSDAY,
            "SEXTA", DayOfWeek.FRIDAY,
            "SABADO", DayOfWeek.SATURDAY,
            "DOMINGO", DayOfWeek.SUNDAY);

    /**
     * Converte o DTO já validado no modelo imutável de domínio {@link AgendaConfiguracao}.
     */
    public AgendaConfiguracao paraDominio() {
        List<Compromisso> listaCompromissos = compromissos.stream()
                .map(CompromissoDto::paraDominio)
                .toList();
        List<BlocoManual> listaBlocosManuais = blocosManuais == null
                ? List.of()
                : blocosManuais.stream().map(BlocoManualDto::paraDominio).toList();
        return new AgendaConfiguracao(
                jornada.paraDominio(),
                margens.paraDominio(),
                classificacao.paraDominio(),
                listaCompromissos,
                listaBlocosManuais);
    }

    @HorarioIntervaloValido(
            permitirCruzarMeiaNoite = false,
            message = "horário final deve ser posterior ao início e não pode cruzar a meia-noite")
    public record JornadaDto(
            @NotNull(message = "início é obrigatório")
            @Pattern(regexp = REGEX_HORARIO, message = "horário inválido. Use HH:mm entre 00:00 e 23:59")
            @JsonProperty("inicio")
            String inicio,

            @NotNull(message = "fim é obrigatório")
            @Pattern(regexp = REGEX_HORARIO, message = "horário inválido. Use HH:mm entre 00:00 e 23:59")
            @JsonProperty("fim")
            String fim) implements IntervaloTemporal {

        Jornada paraDominio() {
            return new Jornada(LocalTime.parse(inicio), LocalTime.parse(fim));
        }
    }

    public record MargensDto(
            @NotNull(message = "deslocamento_entre_locais_min é obrigatório")
            @PositiveOrZero(message = "deslocamento_entre_locais_min deve ser maior ou igual a zero")
            @JsonProperty("deslocamento_entre_locais_min")
            Integer deslocamentoEntreLocaisMin,

            @NotNull(message = "por_empresa é obrigatório")
            @Valid
            @JsonProperty("por_empresa")
            Map<@NotBlank(message = "nome da empresa não pode ser vazio") String,
                @NotNull(message = "margem da empresa não pode ser nula") @Valid MargemEmpresaDto> porEmpresa) {

        Margens paraDominio() {
            Map<String, MargemEmpresa> mapa = new LinkedHashMap<>();
            porEmpresa.forEach((empresa, dto) -> mapa.put(empresa, dto.paraDominio()));
            return new Margens(deslocamentoEntreLocaisMin, mapa);
        }
    }

    public record MargemEmpresaDto(
            @NotNull(message = "antes_min é obrigatório")
            @PositiveOrZero(message = "antes_min deve ser maior ou igual a zero")
            @JsonProperty("antes_min")
            Integer antesMin,

            @NotNull(message = "depois_min é obrigatório")
            @PositiveOrZero(message = "depois_min deve ser maior ou igual a zero")
            @JsonProperty("depois_min")
            Integer depoisMin) {

        MargemEmpresa paraDominio() {
            return new MargemEmpresa(antesMin, depoisMin);
        }
    }

    public record ClassificacaoDto(
            @NotNull(message = "profundo_minimo_min é obrigatório")
            @Positive(message = "profundo_minimo_min deve ser maior que zero")
            @JsonProperty("profundo_minimo_min")
            Integer profundoMinimoMin,

            @NotNull(message = "interrompivel_minimo_min é obrigatório")
            @Positive(message = "interrompivel_minimo_min deve ser maior que zero")
            @JsonProperty("interrompivel_minimo_min")
            Integer interrompivelMinimoMin) {

        Classificacao paraDominio() {
            return new Classificacao(profundoMinimoMin, interrompivelMinimoMin);
        }
    }

    @HorarioIntervaloValido(
            permitirCruzarMeiaNoite = true,
            message = "horário final não pode ser igual ao início")
    public record CompromissoDto(
            @NotNull(message = "dia é obrigatório")
            @Pattern(
                    regexp = REGEX_DIA,
                    message = "dia inválido. Use SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO ou DOMINGO")
            @JsonProperty("dia")
            String dia,

            @NotNull(message = "início é obrigatório")
            @Pattern(regexp = REGEX_HORARIO, message = "horário inválido. Use HH:mm entre 00:00 e 23:59")
            @JsonProperty("inicio")
            String inicio,

            @NotNull(message = "fim é obrigatório")
            @Pattern(regexp = REGEX_HORARIO, message = "horário inválido. Use HH:mm entre 00:00 e 23:59")
            @JsonProperty("fim")
            String fim,

            @NotNull(message = "tipo é obrigatório")
            @Pattern(
                    regexp = REGEX_TIPO_COMPROMISSO,
                    message = "tipo inválido. Use AULA, REUNIAO, ALMOCO, DESLOCAMENTO, TREINO ou PLANTAO")
            @JsonProperty("tipo")
            String tipo,

            @JsonProperty("empresa")
            String empresa,

            @JsonProperty("local")
            String local,

            @JsonProperty("titulo")
            String titulo) implements IntervaloTemporal {

        Compromisso paraDominio() {
            LocalTime horaInicio = LocalTime.parse(inicio);
            LocalTime horaFim = LocalTime.parse(fim);
            Duration duracao = Duration.between(horaInicio, horaFim);
            if (duracao.isNegative()) {
                duracao = duracao.plusDays(1);
            }
            return new Compromisso(
                    DIAS_SEMANA.get(dia),
                    horaInicio,
                    duracao,
                    TipoCompromisso.valueOf(tipo),
                    empresa,
                    local,
                    titulo);
        }
    }

    @HorarioIntervaloValido(
            permitirCruzarMeiaNoite = false,
            message = "horário final deve ser posterior ao início e não pode cruzar a meia-noite")
    public record BlocoManualDto(
            @NotNull(message = "dia é obrigatório")
            @Pattern(
                    regexp = REGEX_DIA,
                    message = "dia inválido. Use SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO ou DOMINGO")
            @JsonProperty("dia")
            String dia,

            @NotNull(message = "início é obrigatório")
            @Pattern(regexp = REGEX_HORARIO, message = "horário inválido. Use HH:mm entre 00:00 e 23:59")
            @JsonProperty("inicio")
            String inicio,

            @NotNull(message = "fim é obrigatório")
            @Pattern(regexp = REGEX_HORARIO, message = "horário inválido. Use HH:mm entre 00:00 e 23:59")
            @JsonProperty("fim")
            String fim,

            @NotNull(message = "tipo é obrigatório")
            @Pattern(
                    regexp = REGEX_TIPO_BLOCO,
                    message = "tipo inválido. Use PROFUNDO, INTERROMPIVEL ou VIGILANCIA")
            @JsonProperty("tipo")
            String tipo,

            @JsonProperty("descricao")
            String descricao) implements IntervaloTemporal {

        BlocoManual paraDominio() {
            return new BlocoManual(
                    DIAS_SEMANA.get(dia),
                    LocalTime.parse(inicio),
                    LocalTime.parse(fim),
                    TipoBloco.valueOf(tipo),
                    descricao);
        }
    }

    interface IntervaloTemporal {
        String inicio();

        String fim();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = HorarioIntervaloValidator.class)
    @interface HorarioIntervaloValido {
        String message();

        boolean permitirCruzarMeiaNoite();

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Valida a relação entre {@code inicio} e {@code fim} quando ambos já possuem formato {@code HH:mm},
     * atribuindo a violação diretamente ao nó {@code fim}.
     */
    public static class HorarioIntervaloValidator
            implements ConstraintValidator<HorarioIntervaloValido, IntervaloTemporal> {

        private boolean permitirCruzarMeiaNoite;

        @Override
        public void initialize(HorarioIntervaloValido constraintAnnotation) {
            this.permitirCruzarMeiaNoite = constraintAnnotation.permitirCruzarMeiaNoite();
        }

        @Override
        public boolean isValid(IntervaloTemporal valor, ConstraintValidatorContext contexto) {
            if (valor == null
                    || valor.inicio() == null
                    || valor.fim() == null
                    || !valor.inicio().matches(REGEX_HORARIO)
                    || !valor.fim().matches(REGEX_HORARIO)) {
                return true;
            }
            LocalTime horaInicio = LocalTime.parse(valor.inicio());
            LocalTime horaFim = LocalTime.parse(valor.fim());
            boolean valido = permitirCruzarMeiaNoite
                    ? !horaFim.equals(horaInicio)
                    : horaFim.isAfter(horaInicio);
            if (!valido) {
                contexto.disableDefaultConstraintViolation();
                contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("fim")
                        .addConstraintViolation();
            }
            return valido;
        }
    }
}
