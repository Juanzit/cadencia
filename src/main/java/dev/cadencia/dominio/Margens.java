package dev.cadencia.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Conjunto imutável de margens configuradas: deslocamento entre locais e margens por empresa.
 */
public record Margens(
        int deslocamentoEntreLocaisMin,
        Map<String, MargemEmpresa> porEmpresa) {

    public Margens {
        if (deslocamentoEntreLocaisMin < 0) {
            throw new IllegalArgumentException("deslocamentoEntreLocaisMin não pode ser negativo");
        }
        Objects.requireNonNull(porEmpresa, "porEmpresa não pode ser nulo");
        Map<String, MargemEmpresa> copia = new LinkedHashMap<>();
        for (Map.Entry<String, MargemEmpresa> entrada : porEmpresa.entrySet()) {
            String empresa = entrada.getKey();
            if (empresa == null || empresa.isBlank()) {
                throw new IllegalArgumentException("nome da empresa em porEmpresa não pode ser nulo ou vazio");
            }
            MargemEmpresa margem = Objects.requireNonNull(
                    entrada.getValue(), "margem da empresa não pode ser nula");
            copia.put(empresa, margem);
        }
        porEmpresa = Collections.unmodifiableMap(copia);
    }
}
