package dev.cadencia.agenda;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cadencia.dominio.AgendaConfiguracao;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter responsável pela leitura, validação sintática/estrutural e conversão de {@code agenda.yaml}.
 */
public class YamlAgendaRepository implements AgendaRepository {

    private final ObjectMapper yamlMapper;
    private final Validator validator;

    public YamlAgendaRepository(ObjectMapper yamlMapper, Validator validator) {
        this.yamlMapper = Objects.requireNonNull(yamlMapper, "yamlMapper não pode ser nulo");
        this.validator = Objects.requireNonNull(validator, "validator não pode ser nulo");
    }

    @Override
    public AgendaConfiguracao carregar(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        AgendaDto dto = lerDto(caminho);
        validarDto(caminho, dto);
        return dto.paraDominio();
    }

    private AgendaDto lerDto(Path caminho) {
        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            AgendaDto dto = yamlMapper.readValue(reader, AgendaDto.class);
            if (dto == null) {
                throw new DadosInvalidosException(
                        "Erro ao ler " + caminho + ": arquivo YAML vazio ou nulo.\n"
                                + "Preencha as seções jornada, margens, classificacao e compromissos.");
            }
            return dto;
        } catch (JsonProcessingException e) {
            throw new DadosInvalidosException(formatarErroSintaxe(caminho, e), e);
        } catch (IOException e) {
            throw new DadosInvalidosException(formatarErroLeitura(caminho), e);
        }
    }

    private void validarDto(Path caminho, AgendaDto dto) {
        Set<ConstraintViolation<AgendaDto>> violacoes = validator.validate(dto);
        if (violacoes.isEmpty()) {
            return;
        }
        String detalhes = violacoes.stream()
                .sorted(Comparator
                        .comparing((ConstraintViolation<AgendaDto> v) -> v.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .map(v -> "campo '" + v.getPropertyPath() + "': " + v.getMessage())
                .collect(Collectors.joining("\n"));

        throw new DadosInvalidosException("Dados inválidos em " + caminho + ":\n" + detalhes);
    }

    private static String formatarErroSintaxe(Path caminho, JsonProcessingException e) {
        JsonLocation localizacao = e.getLocation();
        String onde = localizacao != null && localizacao.getLineNr() > 0
                ? " na linha " + localizacao.getLineNr()
                        + (localizacao.getColumnNr() > 0 ? ", coluna " + localizacao.getColumnNr() : "")
                : "";
        return "Erro ao ler " + caminho + ": sintaxe YAML inválida" + onde + ".\n"
                + "Verifique a indentação e a estrutura das propriedades.";
    }

    private static String formatarErroLeitura(Path caminho) {
        return "Não foi possível ler " + caminho + ":\n"
                + "arquivo inexistente ou sem permissão de leitura.\n"
                + "Verifique o caminho e as permissões do arquivo.";
    }
}
