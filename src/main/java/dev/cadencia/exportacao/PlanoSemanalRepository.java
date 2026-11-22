package dev.cadencia.exportacao;

import java.nio.file.Path;

/**
 * Porta de persistência para armazenamento do plano semanal exportado.
 */
public interface PlanoSemanalRepository {

    /**
     * Grava o conteúdo do plano semanal no caminho de destino informado, criando diretórios pais
     * quando necessário e sobrescrevendo eventual arquivo existente.
     */
    Path salvar(Path destino, String conteudo);
}
