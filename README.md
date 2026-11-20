# Cadência

> Planejador semanal que roda 100% local: calcula seus blocos de tempo livre, distribui tarefas de estudo conforme a qualidade de cada bloco e usa um modelo local (Ollama) para gerar roteiros de aula e o feedback da semana.

**Status:** Fase 1 em andamento — Setup Inicial (Tarefas 1.1 a 1.3 concluídas; 1.4 em andamento).

## Stack

Java 21 · Spring Boot · Picocli · Jackson YAML · JUnit 5 · ArchUnit · JaCoCo · GitHub Actions · Ollama (Fase 6)

## Princípios

- **Local primeiro:** nenhum dado pessoal ou material de empresa sai da máquina.
- **Planejamento determinístico:** a IA só gera texto.
- **Dados pessoais fora do Git:** `dados/` ignorado; só exemplos sintéticos versionados.

## Documentação de desenvolvimento

Toda a documentação técnica fica em [`dev/`](dev/README.md): requisitos, regras, arquitetura, design de saída, memória do projeto e os planos de fase em `dev/tasks/`.
