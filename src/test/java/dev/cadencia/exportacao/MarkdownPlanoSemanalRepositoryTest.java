package dev.cadencia.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownPlanoSemanalRepositoryTest {

    private final PlanoSemanalRepository repository = new MarkdownPlanoSemanalRepository();

    @TempDir
    Path tempDir;

    @Test
    void deveCriarDiretoriosPaisGravarEmUtf8ComLfESobrescreverSemDuplicar() throws IOException {
        Path destino = tempDir.resolve("dados/semana/2026-W48.md");

        Path salvo = repository.salvar(destino, "# Semana 2026-W48\r\nLinha com acentuação: çãõ\r\n");

        assertThat(salvo).isEqualTo(destino);
        assertThat(Files.exists(destino)).isTrue();
        byte[] bytes = Files.readAllBytes(destino);
        String lido = new String(bytes, StandardCharsets.UTF_8);
        assertThat(lido)
                .isEqualTo("# Semana 2026-W48\nLinha com acentuação: çãõ\n")
                .doesNotContain("\r");

        // Segunda gravação sobrescreve completamente sem concatenar nem duplicar conteúdo
        repository.salvar(destino, "# Conteúdo atualizado\n");
        assertThat(Files.readString(destino, StandardCharsets.UTF_8))
                .isEqualTo("# Conteúdo atualizado\n")
                .doesNotContain("Linha com acentuação");
    }

    @Test
    void deveValidarNulosELancarUncheckedIoExceptionQuandoDestinoEhDiretorio() throws IOException {
        assertThatNullPointerException().isThrownBy(() -> repository.salvar(null, "abc"));
        assertThatNullPointerException().isThrownBy(() -> repository.salvar(tempDir.resolve("a.md"), null));

        Path diretorioExistente = tempDir.resolve("pasta-ocupada");
        Files.createDirectories(diretorioExistente);
        assertThatThrownBy(() -> repository.salvar(diretorioExistente, "# Teste\n"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
