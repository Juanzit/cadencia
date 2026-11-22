package dev.cadencia.exportacao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Adapter de arquivo responsável por gravar planos semanais Markdown em UTF-8 com quebras de linha
 * LF, criando automaticamente os diretórios pais e sobrescrevendo o destino sem duplicação.
 */
public class MarkdownPlanoSemanalRepository implements PlanoSemanalRepository {

    @Override
    public Path salvar(Path destino, String conteudo) {
        Objects.requireNonNull(destino, "destino não pode ser nulo");
        Objects.requireNonNull(conteudo, "conteudo não pode ser nulo");

        String conteudoLf = conteudo.replace("\r\n", "\n").replace('\r', '\n');
        try {
            Path diretorioPai = destino.getParent();
            if (diretorioPai != null) {
                Files.createDirectories(diretorioPai);
            }
            return Files.writeString(
                    destino,
                    conteudoLf,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Erro ao gravar plano semanal em " + destino, ex);
        }
    }
}
