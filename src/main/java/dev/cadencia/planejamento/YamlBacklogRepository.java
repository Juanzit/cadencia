package dev.cadencia.planejamento;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.Tarefa;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter responsável pela leitura, validação sintática/estrutural e conversão de {@code backlog.yaml}.
 */
public class YamlBacklogRepository implements BacklogRepository {

    private final ObjectMapper yamlMapper;
    private final Validator validator;

    public YamlBacklogRepository(ObjectMapper yamlMapper, Validator validator) {
        this.yamlMapper = Objects.requireNonNull(yamlMapper, "yamlMapper não pode ser nulo");
        this.validator = Objects.requireNonNull(validator, "validator não pode ser nulo");
    }

    @Override
    public List<Tarefa> carregar(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        BacklogDto dto = lerDto(caminho);
        validarDto(caminho, dto);
        return dto.paraDominio();
    }

    @Override
    public Optional<List<Tarefa>> carregarSeExistir(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        if (!Files.exists(caminho)) {
            return Optional.empty();
        }
        return Optional.of(carregar(caminho));
    }

    private BacklogDto lerDto(Path caminho) {
        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            BacklogDto dto = yamlMapper.readValue(reader, BacklogDto.class);
            if (dto == null) {
                throw erroArquivoVazio(caminho);
            }
            return dto;
        } catch (MismatchedInputException e) {
            if (e.getMessage() != null && e.getMessage().contains("No content to map")) {
                throw erroArquivoVazio(caminho, e);
            }
            throw new DadosInvalidosException(formatarErroSintaxe(caminho, e), e);
        } catch (JsonProcessingException e) {
            throw new DadosInvalidosException(formatarErroSintaxe(caminho, e), e);
        } catch (IOException e) {
            throw new DadosInvalidosException(formatarErroLeitura(caminho), e);
        }
    }

    private void validarDto(Path caminho, BacklogDto dto) {
        Set<ConstraintViolation<BacklogDto>> violacoes = validator.validate(dto);
        if (violacoes.isEmpty()) {
            return;
        }
        String detalhes = violacoes.stream()
                .sorted(Comparator
                        .comparing((ConstraintViolation<BacklogDto> v) -> v.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .map(v -> "campo '" + v.getPropertyPath() + "': " + v.getMessage())
                .collect(Collectors.joining("\n"));

        throw new DadosInvalidosException("Dados inválidos em " + caminho + ":\n" + detalhes);
    }

    private static DadosInvalidosException erroArquivoVazio(Path caminho) {
        return new DadosInvalidosException(mensagemArquivoVazio(caminho));
    }

    private static DadosInvalidosException erroArquivoVazio(Path caminho, Throwable causa) {
        return new DadosInvalidosException(mensagemArquivoVazio(caminho), causa);
    }

    private static String mensagemArquivoVazio(Path caminho) {
        return "Erro ao ler " + caminho + ": arquivo YAML vazio ou nulo.\n"
                + "Preencha a seção tarefas com as atividades de estudo.";
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
