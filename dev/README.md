# Cadência

> Planejador semanal que roda 100% local: calcula seus blocos de tempo livre, distribui tarefas de estudo conforme a qualidade de cada bloco e usa um modelo local (Ollama) para gerar roteiros de aula e o feedback da semana.

*Documentação interna de desenvolvimento.*

## Como este repositório está organizado

| Arquivo | Para que serve |
| :--- | :--- |
| `dev/PRD.md` | Problema, requisitos, fases e gates |
| `dev/RULES.md` | Regras de desenvolvimento e padrões obrigatórios |
| `dev/ARCHITECTURE.md` | Camadas, pacotes, fluxo de dados e caminho de escalabilidade |
| `dev/DESIGN.md` | Formato dos Markdown gerados e comportamento da CLI |
| `dev/GEMINI.md` | Memória do projeto: ADRs, perguntas em aberto e log |
| `dev/tasks/` | Uma fase por arquivo, com tarefas numeradas e gate de verificação |

## Fases

| # | Arquivo | Entrega | Status |
| :---: | :--- | :--- | :--- |
| 1–2 | `dev/tasks/_1_TASKS_SETUP.md` | Setup + blocos livres da semana | 🟡 Em Andamento (3/14 · Tarefa 1.4 em andamento) |
| 3 | `dev/tasks/_2_TASKS_ALOCACAO_ESTUDOS.md` | Estudos alocados nos blocos | 🔴 Não Iniciado |
| 4 | `dev/tasks/_3_TASKS_FEEDBACK_CONFIABILIDADE.md` | Feedback e aprendizado de horários | 🔴 Não Iniciado |
| 5 | `dev/tasks/_4_TASKS_TURMAS.md` | Previsão de turmas | 🔴 Não Iniciado |
| 6 | `dev/tasks/_5_TASKS_IA_LOCAL.md` | Roteiros e resumo com Ollama | 🔴 Não Iniciado |
| 7 | `dev/tasks/_6_TASKS_PORTFOLIO.md` | Release v0.1.0 | 🔴 Não Iniciado |

**Comece por `dev/tasks/_1_TASKS_SETUP.md`.** Cada fase só avança quando o gate da anterior está aprovado.

## Stack

Java 21 · Spring Boot · Picocli · Jackson YAML · JUnit 5 · JaCoCo · GitHub Actions · Ollama (Fase 6)

## Princípios

- **Local primeiro:** nenhum dado pessoal ou material de empresa sai da máquina.
- **Planejamento determinístico:** a IA só gera texto.
- **Dados pessoais fora do Git:** `dados/` ignorado; só exemplos sintéticos versionados.
