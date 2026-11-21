# 🧠 Project Memory (Memória do Projeto e Arquivo de Decisões)

**Cadência – Registro Histórico de Decisões de Arquitetura (ADR)**

| Campo | Informação |
| :--- | :--- |
| **Última Atualização** | Novembro de 2026 |
| **Fase Atual** | Fase 2 em andamento — Núcleo de Agenda (Tarefa 2.2 concluída) |
| **Status Geral** | Tarefa 2.2 concluída (`dev/tasks/_1_TASKS_SETUP.md` — 7/14 · 50%); próxima etapa: Tarefa 2.3 (`AgendaService` e margens) |

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
| 2026-11-20 | Fase 1 | Tarefa 1.2 concluída: projeto Maven (`dev.cadencia:cadencia:0.1.0-SNAPSHOT`, `jar`, Wrapper 3.9.11), Java 21 (`<release>21</release>`, `-Xlint:all,-processing`), Spring Boot `3.5.7`, `picocli` `4.7.7`, `jackson-dataformat-yaml` e `jackson-datatype-jsr310` (`2.19.2`), `spring-boot-starter-validation`, `spring-boot-starter-test`, `archunit-junit5` `1.4.1`, JaCoCo `0.8.13`, `CadenciaApplication`, `application.yaml`, `CadenciaApplicationTest` e `ArquiteturaTest` | `.\mvnw.cmd -B clean verify` verde (5/5 testes: 1 Spring + 4 ArchUnit; `target/site/jacoco/index.html` gerado). `ArquiteturaTest` usa `allowEmptyShould(true)` **temporariamente** na Fase 1 (remoção obrigatória no Gate 2). Surefire preserva `@{argLine}` + `-XX:+EnableDynamicAgentLoading -Xshare:off`. `ArquiteturaTest` foi antecipado da 1.3 para a 1.2; pacotes e `package-info.java` seguem na 1.3 |
| 2026-11-20 | Fase 1 | Commit da Tarefa 1.2 (`719c94f`) publicado em `origin/dev` (`mvnw` `100755`); Tarefa 1.3 concluída com a criação dos 10 pacotes e `package-info.java` (`cli`, `agenda`, `planejamento`, `planejamento.strategies`, `feedback`, `turmas`, `ia`, `exportacao`, `exportacao.strategies`, `dominio`) | Cada `package-info.java` documenta as responsabilidades e restrições da arquitetura modular (`dev/ARCHITECTURE.md`, `dev/RULES.md`). `.\mvnw.cmd -B clean verify` verde (`11` fontes main + `2` teste em Java 21 sem warnings; `5/5` testes incluindo as 4 regras do ArchUnit; JaCoCo gerado). `allowEmptyShould(true)` mantido temporariamente até o Gate 2. Próxima tarefa: `1.4` |
| 2026-11-20 | Fase 1 | Commit da Tarefa 1.3 (`0429efe`) publicado em `origin/dev`; criado `.github/workflows/ci.yml` (`push` e `pull_request` em `main` e `dev`, `actions/checkout@v4`, `actions/setup-java@v4` Temurin Java `21` + `cache: maven`, `./mvnw -B clean verify`) | `.\mvnw.cmd -B clean verify` verde localmente (`5/5` testes: 1 Spring + 4 ArchUnit; JaCoCo `target/site/jacoco/index.html` gerado; sem warnings). Tarefa 1.4 mantida `🟡 Em Andamento` até validação remota da CI no GitHub. Próxima etapa: revisão de `ci.yml` e execução remota |
| 2026-11-20 | Fase 1 | Commit da Tarefa 1.4 (`f3380de`) publicado em `origin/dev`; Tarefa 1.5 concluída com criação de `exemplos/agenda.exemplo.yaml` (sintético, `EmpresaA`/`EmpresaB`) e `dados/.gitkeep`, mantendo `.gitignore` com `dados/*` e `!dados/.gitkeep` | `git check-ignore` confirmou `dados/agenda.yaml` e `dados/feedback/2026-W48.md` ignorados e `dados/.gitkeep` / `exemplos/agenda.exemplo.yaml` liberados. Sintaxe YAML validada via SnakeYAML `2.4` (`5` chaves, `18` compromissos; validação semântica fica para a Tarefa 2.2). `.\mvnw.cmd -B clean verify` verde (`5/5` testes; JaCoCo gerado). Tarefa 1.4 segue `🟡 Em Andamento` aguardando confirmação da CI no GitHub. Próxima etapa: aprovação do Gate 1 |
| 2026-11-20 | Fase 1 | Commit da Tarefa 1.5 (`9e8467d`) publicado em `origin/dev`; Tarefa 1.4 concluída após CI verde no GitHub (`run 44834023290` em `f3380de` e `run 44835991184` em `9e8467d`); **Gate 1 aprovado** (4/4 itens) e **Fase 1 concluída**; `README.md` atualizado com instruções de compilação, teste e execução do bootstrap | `.\mvnw.cmd -B clean verify` verde localmente e CI verde em Ubuntu; `dados/.gitkeep` versionado sem dados pessoais em `dados/`. Próxima etapa: Tarefa 2.1 (núcleo de domínio) |
| 2026-11-20 | Fase 2 | Tarefa 2.1 concluída: criados em `dev.cadencia.dominio` os tipos `TipoBloco`, `TipoCompromisso`, `Compromisso`, `Bloco` e `Semana`, com 16 testes unitários (`CompromissoTest`, `BlocoTest`, `SemanaTest`, `TiposDominioTest`) | Invariantes: `Objects.requireNonNull`, `0 < duracao < 24h`, `Bloco` com `fim > inicio`, `Semana` `1..53` e `List.copyOf`. `Compromisso` usa intervalo semiaberto `[inicio, fim)`: término exato às `00:00` retorna `cruzaMeiaNoite() == false`; `fatiarPorDia()` gera 2 fragmentos quando cruza e preserva domingo→segunda para descarte/aviso na Tarefa 2.4. `.\mvnw.cmd -B clean verify` verde (`21/21` testes, `4/4` ArchUnit com isolamento puro de domínio sem Spring/Jackson/I/O, JaCoCo `100%` linhas `48/48` e `100%` instruções `282/282` em `dominio`) |
| 2026-11-20 | Fase 2 | Commit da Tarefa 2.1 (`e818e15`) publicado em `origin/dev`; Tarefa 2.2 concluída com configuração completa no domínio (`Jornada`, `MargemEmpresa`, `Margens`, `Classificacao`, `BlocoManual`, `AgendaConfiguracao`) e leitura validada em `dev.cadencia.agenda` (`AgendaDto`, `AgendaRepository`, `YamlAgendaRepository`, `DadosInvalidosException`) | `AgendaDto` mantém `dia`, `inicio`, `fim` e `tipo` como `String` com mapeamento explícito `snake_case`, Bean Validation + `HorarioIntervaloValidator` (nó `fim`) antes da conversão (`Duration.between` + `plusDays(1)` se negativo). `YamlAgendaRepository` separa erro de sintaxe (`JsonLocation` linha/coluna), erro de campo (`propertyPath` ordenado, sem linha/coluna) e `IOException` (ADR-011). `.\mvnw.cmd -B clean verify` verde (`33/33` testes, `4/4` ArchUnit); JaCoCo: `dominio` `100%` linhas (`98/98`) e instruções (`506/506`); `agenda` `100%` linhas (`95/95`) e `99.6%` instruções (`478/480`). Próxima etapa: Tarefa 2.3 |
