# Cadência

> Planejador semanal que roda 100% local: calcula seus blocos de tempo livre, distribui tarefas de estudo conforme a qualidade de cada bloco e usa um modelo local (Ollama) para gerar roteiros de aula e o feedback da semana.

**Status:** Fase 2 em andamento — Núcleo de Agenda (carregamento YAML e expansão de compromissos, margens e deslocamentos implementados até a Tarefa 2.3; cálculo de blocos livres entra na Tarefa 2.4).

## Stack

Java 21 · Spring Boot · Picocli · Jackson YAML · JUnit 5 · ArchUnit · JaCoCo · GitHub Actions · Ollama (Fase 6)

## Como compilar e testar

**Pré-requisito:** JDK Java 21 instalado.

**Linux/macOS:**

```bash
./mvnw clean verify
```

**Windows:**

```powershell
.\mvnw.cmd clean verify
```

**Como iniciar o bootstrap atual:**

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

> Os comandos funcionais da CLI (`cadencia semana`, etc.) serão disponibilizados nas etapas posteriores do projeto; nesta etapa existe apenas o bootstrap Spring Boot da infraestrutura.

## Princípios

- **Local primeiro:** nenhum dado pessoal ou material de empresa sai da máquina.
- **Planejamento determinístico:** a IA só gera texto.
- **Dados pessoais fora do Git:** `dados/` ignorado; só exemplos sintéticos versionados.

## Documentação de desenvolvimento

Toda a documentação técnica fica em [`dev/`](dev/README.md): requisitos, regras, arquitetura, design de saída, memória do projeto e os planos de fase em `dev/tasks/`.
