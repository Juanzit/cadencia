package dev.cadencia.turmas;

import dev.cadencia.agenda.DadosInvalidosException;
import dev.cadencia.dominio.Turma;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter CSV responsável pela leitura UTF-8, validação linha a linha via {@link TurmaDto} e
 * Bean Validation, e conversão determinística de {@code turmas.csv} para {@link Turma}s.
 *
 * <p><b>Convenção de localização:</b> erros globais de leitura sem linha física válida (arquivo
 * inexistente ou sem permissão, codificação inválida ou arquivo CSV vazio) referenciam
 * explicitamente {@code linha 1}, posição esperada para o cabeçalho obrigatório.</p>
 */
public class CsvTurmaRepository implements TurmaRepository {

    private static final String BOM_UTF8 = "\uFEFF";
    private static final int LINHA_CABECALHO_ESPERADO = 1;
    private static final List<String> CABECALHO_CANONICO = List.of(
            "turma", "empresa", "modulo_atual", "inicio_modulo", "duracao_semanas");
    private static final String CABECALHO_TEXTO = String.join(",", CABECALHO_CANONICO);

    private final Validator validator;

    public CsvTurmaRepository(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator não pode ser nulo");
    }

    @Override
    public boolean existe(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        return Files.exists(caminho);
    }

    @Override
    public List<Turma> carregar(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        List<String> linhas = lerLinhasUtf8(caminho);
        return parsearCsv(caminho, linhas);
    }

    @Override
    public Optional<List<Turma>> carregarSeExistir(Path caminho) {
        Objects.requireNonNull(caminho, "caminho não pode ser nulo");
        if (!existe(caminho)) {
            return Optional.empty();
        }
        return Optional.of(carregar(caminho));
    }

    private List<Turma> parsearCsv(Path caminho, List<String> linhas) {
        boolean viuCabecalho = false;
        List<Turma> turmas = new ArrayList<>();
        Map<String, Integer> linhaPorCodigo = new HashMap<>();

        for (int i = 0; i < linhas.size(); i++) {
            int numeroLinha = i + 1;
            String linha = limparLinhaInicial(i, linhas.get(i));
            if (linha.isEmpty()) {
                continue;
            }
            if (!viuCabecalho) {
                validarCabecalho(caminho, numeroLinha, linha);
                viuCabecalho = true;
                continue;
            }
            Turma turma = parsearLinhaDados(caminho, numeroLinha, linha);
            String chave = turma.codigo().toLowerCase(Locale.ROOT);
            Integer linhaAnterior = linhaPorCodigo.putIfAbsent(chave, numeroLinha);
            if (linhaAnterior != null) {
                throw erroLinha(
                        caminho,
                        numeroLinha,
                        "código de turma \""
                                + turma.codigo()
                                + "\" duplicado (já declarado na linha "
                                + linhaAnterior
                                + "). Cada turma deve possuir código único.");
            }
            turmas.add(turma);
        }

        if (!viuCabecalho) {
            throw erroLinha(
                    caminho,
                    LINHA_CABECALHO_ESPERADO,
                    "arquivo CSV vazio. Informe o cabeçalho obrigatório \""
                            + CABECALHO_TEXTO
                            + "\".");
        }
        return List.copyOf(turmas);
    }

    private static List<String> lerLinhasUtf8(Path caminho) {
        try {
            return Files.readAllLines(caminho, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw erroLinha(
                    caminho,
                    LINHA_CABECALHO_ESPERADO,
                    "codificação inválida; não foi possível decodificar o arquivo como UTF-8. Salve o arquivo CSV em UTF-8.",
                    e);
        } catch (IOException e) {
            throw erroLinha(
                    caminho,
                    LINHA_CABECALHO_ESPERADO,
                    "não foi possível ler o arquivo (arquivo inexistente ou sem permissão de leitura). Verifique o caminho e as permissões do arquivo.",
                    e);
        }
    }

    private static String limparLinhaInicial(int indiceLinha, String bruta) {
        String limpa = indiceLinha == 0 && bruta.startsWith(BOM_UTF8) ? bruta.substring(1) : bruta;
        return limpa.trim();
    }

    private static void validarCabecalho(Path caminho, int numeroLinha, String linha) {
        List<String> colunas = dividirColunasCsv(linha, caminho, numeroLinha);
        if (!colunas.equals(CABECALHO_CANONICO)) {
            throw erroLinha(
                    caminho,
                    numeroLinha,
                    "cabeçalho CSV inválido \""
                            + linha
                            + "\". Esperado \""
                            + CABECALHO_TEXTO
                            + "\".");
        }
    }

    private Turma parsearLinhaDados(Path caminho, int numeroLinha, String linha) {
        List<String> colunas = dividirColunasCsv(linha, caminho, numeroLinha);
        if (colunas.size() != CABECALHO_CANONICO.size()) {
            throw erroLinha(
                    caminho,
                    numeroLinha,
                    "número de colunas inválido (esperado "
                            + CABECALHO_CANONICO.size()
                            + ", encontrado "
                            + colunas.size()
                            + "). Formato esperado: "
                            + CABECALHO_TEXTO
                            + ".");
        }

        LocalDate inicioModulo = parsearDataOpcional(colunas.get(3), caminho, numeroLinha);
        Integer duracaoSemanas = parsearInteiroOpcional(colunas.get(4), caminho, numeroLinha);

        TurmaDto dto = new TurmaDto(
                colunas.get(0),
                colunas.get(1),
                colunas.get(2),
                inicioModulo,
                duracaoSemanas);
        validarDto(caminho, numeroLinha, dto);
        return dto.paraDominio();
    }

    private static LocalDate parsearDataOpcional(String valor, Path caminho, int numeroLinha) {
        if (valor.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw erroLinha(
                    caminho,
                    numeroLinha,
                    "data inválida em 'inicio_modulo' (\""
                            + valor
                            + "\"). Use o formato AAAA-MM-DD.",
                    e);
        }
    }

    private static Integer parsearInteiroOpcional(String valor, Path caminho, int numeroLinha) {
        if (valor.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            throw erroLinha(
                    caminho,
                    numeroLinha,
                    "valor inválido em 'duracao_semanas' (\""
                            + valor
                            + "\"). Use um número inteiro positivo.",
                    e);
        }
    }

    private void validarDto(Path caminho, int numeroLinha, TurmaDto dto) {
        Set<ConstraintViolation<TurmaDto>> violacoes = validator.validate(dto);
        if (violacoes.isEmpty()) {
            return;
        }
        String detalhes = violacoes.stream()
                .sorted(Comparator
                        .comparing((ConstraintViolation<TurmaDto> v) -> v.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .map(v -> "campo '" + v.getPropertyPath() + "': " + v.getMessage())
                .collect(Collectors.joining("; "));
        throw erroLinha(caminho, numeroLinha, detalhes);
    }

    private static List<String> dividirColunasCsv(String linha, Path caminho, int numeroLinha) {
        List<String> colunas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean emAspas = false;
        boolean inicioCampo = true;
        boolean aposFecharAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (emAspas) {
                if (c == '"') {
                    if (i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                        atual.append('"');
                        i++;
                    } else {
                        emAspas = false;
                        aposFecharAspas = true;
                    }
                } else {
                    atual.append(c);
                }
            } else if (c == ',') {
                colunas.add(atual.toString().trim());
                atual.setLength(0);
                inicioCampo = true;
                aposFecharAspas = false;
            } else if (aposFecharAspas) {
                if (!Character.isWhitespace(c)) {
                    throw erroLinha(
                            caminho,
                            numeroLinha,
                            "conteúdo inesperado após fechamento de aspas na linha CSV.");
                }
            } else if (c == '"') {
                if (!inicioCampo) {
                    throw erroLinha(
                            caminho,
                            numeroLinha,
                            "aspas inesperadas em campo CSV não delimitado por aspas.");
                }
                emAspas = true;
                inicioCampo = false;
            } else if (!inicioCampo || !Character.isWhitespace(c)) {
                atual.append(c);
                inicioCampo = false;
            }
        }
        if (emAspas) {
            throw erroLinha(caminho, numeroLinha, "aspas não fechadas na linha CSV.");
        }
        colunas.add(atual.toString().trim());
        return colunas;
    }

    private static DadosInvalidosException erroLinha(Path caminho, int numeroLinha, String detalhe) {
        return new DadosInvalidosException(
                "Erro em " + caminho + " (linha " + numeroLinha + "): " + detalhe);
    }

    private static DadosInvalidosException erroLinha(
            Path caminho, int numeroLinha, String detalhe, Throwable causa) {
        return new DadosInvalidosException(
                "Erro em " + caminho + " (linha " + numeroLinha + "): " + detalhe,
                causa);
    }
}
