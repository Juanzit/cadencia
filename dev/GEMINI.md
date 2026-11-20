# 🧠 Project Memory (Memória do Projeto e Arquivo de Decisões)

**Cadência – Registro Histórico de Decisões de Arquitetura (ADR)**

| Campo | Informação |
| :--- | :--- |
| **Última Atualização** | Novembro de 2026 |
| **Fase Atual** | Fase 1 — Setup Inicial e Infraestrutura (em andamento) |
| **Status Geral** | Tarefa 1.1 concluída (`dev/tasks/_1_TASKS_SETUP.md` — 1/14); aguardando revisão para Tarefa 1.2 |

---

## 📌 Registros de Decisões de Arquitetura (ADRs)

### ADR-001 — Planejamento sem IA
- **Status:** ✅ Aceito
- **Contexto:** o usuário quer que a semana se monte sozinha, com resultado previsível.
- **Decisão:** cálculo de blocos, alocação, confiabilidade e previsão de turmas são código determinístico. O LLM só gera texto (roteiros, resumo semanal).

### ADR-002 — Java com Spring Boot e Picocli
- **Status:** ✅ Aceito
- **Contexto:** o objetivo profissional é Backend Java e o projeto deve servir de portfólio.
- **Decisão:** Java 21, Spring Boot e Picocli para a CLI; evolução natural para API REST.

### ADR-003 — CLI antes de interface web
- **Status:** ✅ Aceito
- **Decisão:** começar por linha de comando para validar a lógica com menor esforço. API REST e web ficam para depois do gate da Fase 7.

### ADR-004 — Arquivos de texto atrás de interfaces Repository
- **Status:** ✅ Aceito
- **Contexto:** YAML, CSV e Markdown são simples e abrem no AppFlowy; mas o projeto precisa escalar.
- **Decisão:** toda leitura/escrita passa por interfaces `*Repository`. Um adapter JPA (SQLite/PostgreSQL) poderá ser adicionado sem alterar services.

### ADR-005 — Dados pessoais fora do repositório
- **Status:** ✅ Aceito
- **Decisão:** `dados/` no `.gitignore`; `exemplos/` com dados sintéticos. Materiais de EmpresaA e EmpresaB nunca entram no Git.

### ADR-006 — Turmas por CSV mensal, sem leitura automática de imagem
- **Status:** ✅ Aceito
- **Contexto:** modelos de visão que cabem numa RTX 3050 erram em tabelas densas.
- **Decisão:** a tabela é extraída uma vez por mês (manualmente ou com assistente online), conferida e colada em `turmas.csv`.

### ADR-007 — Ferramentas adiadas
- **Status:** ✅ Aceito
- **Decisão:** sem Docker, n8n, MCP, Craft Anything ou Continue no início. Entram apenas diante de necessidade concreta (Docker/n8n competem por RAM com os modelos; Copilot já cobre autocompletar).

### ADR-008 — Entrega em fases com gates de verificação
- **Status:** ✅ Aceito
- **Decisão:** cada fase tem um arquivo em `dev/tasks/` com tarefas numeradas e um gate; só se avança com o gate aprovado. A primeira fase resolve a dor principal (blocos livres da semana).

### ADR-009 — Documentação de desenvolvimento em `dev/`
- **Status:** ✅ Aceito
- **Decisão:** PRD, RULES, ARCHITECTURE, DESIGN, GEMINI e o README interno ficam em `dev/`; planos de fase em `dev/tasks/`. A raiz mantém apenas `README.md` de apresentação e um `GEMINI.md` curto apontando para `dev/`.

### ADR-010 — Compromissos como início + duração
- **Status:** ✅ Aceito
- **Contexto:** a regra "fim depois do início" conflita com compromissos que cruzam a meia-noite.
- **Decisão:** `Compromisso` usa `inicio` + `Duration` (0 < duração < 24h); o fim é derivado. No cálculo da semana, compromissos que cruzam a meia-noite são fatiados em dois intervalos (no domingo, a fração da segunda-feira seguinte é descartada na semana atual e registrada como aviso).

### ADR-011 — Erros de sintaxe e de validação separados
- **Status:** ✅ Aceito
- **Decisão:** erro de sintaxe YAML informa linha/coluna (Jackson `JsonLocation`); erro de validação informa o caminho do campo (`compromissos[i].campo`). Não se mapeia linha para erros de validação.

### ADR-012 — Testes de arquitetura com ArchUnit
- **Status:** ✅ Aceito
- **Decisão:** `ArquiteturaTest` garante `dominio` sem Spring/Jackson/I/O e as fronteiras `cli` → `service` → `repository`. `allowEmptyShould(true)` apenas na Fase 1; estrito a partir do Gate 2.

---

## ❓ Perguntas em Aberto

| # | Pergunta | Impacto | Fase afetada |
| :--- | :--- | :--- | :---: |
| Q1 | Qual a VRAM exata da RTX 3050 (6 GB ou 8 GB)? | Escolha do modelo no Ollama | 6 |
| Q2 | Em que formato chega o material padronizado da EmpresaA (PDF, PPTX, portal)? | Extrator de texto | 6 |
| Q3 | A reunião da EmpresaA de quarta, 11h–12h, exige preparação? | Regra de agenda e aviso | 2 |
| Q4 | ~~Qual o nome definitivo do projeto e o `groupId`?~~ ✅ **Decidida:** nome `cadencia`, `groupId` `dev.cadencia` | Estrutura de pacotes | 1 |
| Q5 | A empresa permite estudar durante AE vazia e plantão? | Uso de blocos de vigilância | 2 |

## 📓 Log de Execução

| Data | Fase | O que foi feito | Decisões / Observações |
| :--- | :--- | :--- | :--- |
| 2026-11-20 | Planejamento | Reorganização da documentação em `dev/` e `dev/tasks/`; ajustes pré-código nos planos | ADRs 009–012 aceitos; Q4 decidida (`cadencia` / `dev.cadencia`) |
| 2026-11-20 | Fase 1 | Tarefa 1.1 concluída: `git init` (`main`), remote `origin`, `.gitignore` e `README.md` inicial | Menções reais substituídas por `EmpresaA`/`EmpresaB` antes do commit inicial |
