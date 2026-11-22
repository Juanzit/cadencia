# 🧠 Project Memory (Memória do Projeto e Arquivo de Decisões)

**Cadência – Registro Histórico de Decisões de Arquitetura (ADR)**

| Campo | Informação |
| :--- | :--- |
| **Última Atualização** | Novembro de 2026 |
| **Fase Atual** | Fase 2B em andamento (Tarefas 3.1–3.2 concluídas) — próxima etapa: Tarefa 3.3 (documentação README e Marco 1) |
| **Status Geral** | Tarefas 1.1–2.6, Gate 2 e Tarefas 3.1–3.2 concluídos (`dev/tasks/_1_TASKS_SETUP.md` — 13/14 · 93%); próxima etapa: Tarefa 3.3 |

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
| 2026-11-21 | Fase 2 | Commit da Tarefa 2.2 (`a2cc2d7`) publicado em `origin/dev`; Tarefa 2.3 concluída com `AgendaService`, `AgendaExpandida`, `IntervaloOcupado` e `TipoIntervaloOcupado` (`AgendaServiceTest` com 10 cenários) | `AgendaService` depende apenas de `AgendaRepository` e `Clock` (`America/Sao_Paulo`). Pré-ordena cópia defensiva de `compromissos` (`diaSemana → inicio → duracao → tipo → empresa → local → titulo`, null-safe) antes de expandir por dia, garantindo `intervalosOcupados` e `avisos` independentes da ordem do YAML. Aplica margens por empresa apenas a `AULA` com empresa cadastrada (`> 0` min), gera `DESLOCAMENTO_AUTOMATICO` em mudanças de local normalizado sem `DESLOCAMENTO` explícito intermediário (ignorando nulo/vazio/`TRANSITO`), usa `Compromisso.fatiarPorDia()`, divide margens/deslocamentos na meia-noite e descarta frações fora da semana com avisos explícitos. `.\mvnw.cmd -B clean verify` verde (`43/43` testes, `4/4` ArchUnit); JaCoCo: `dominio` `100%` linhas (`98/98`) e instruções (`506/506`); `agenda` `98,9%` linhas (`277/280`) e `99,0%` instruções (`1248/1260`). Próxima tarefa: `2.4` |
| 2026-11-21 | Fase 2 | Commit da Tarefa 2.3 (`d550252`) publicado em `origin/dev`; Tarefa 2.4 concluída com `IntervaloLivre`, `AgendaIntervalosLivres` e `AgendaService.calcularIntervalosLivres(Path)` (`AgendaServiceTest` com 17 cenários) | Reutiliza `expandir(Path)` preservando `Clock` e fuso `America/Sao_Paulo`; para cada um dos 7 dias ISO, recorta ocupações à janela `[jornada.inicio(), jornada.fim())` em nanossegundos com limite `86_400_000_000_000L` (preservando término em `00:00` sem confundir `LocalTime.MIDNIGHT` com `0`), ignora ocupações sem interseção, mescla sobreposições e adjacências e subtrai da jornada gerando intervalos positivos sem duração zero. Mantém `PLANTAO` como ocupação e não converte `blocos_manuais` nem classifica blocos (escopo da 2.5). `.\mvnw.cmd -B clean verify` verde (`50/50` testes, `4/4` ArchUnit); JaCoCo: `dominio` `100%` linhas (`98/98`) e instruções (`506/506`); `agenda` `99,2%` linhas (`358/361`) e `99,3%` instruções (`1630/1642`). Próxima tarefa: `2.5` |
| 2026-11-21 | Fase 2 | Commit da Tarefa 2.4 (`fe7ce22`) publicado em `origin/dev`; Tarefa 2.5 concluída com `SemanaPlanejada`, `@AgendaDto.BlocosManuaisSemSobreposicao` e `AgendaService.gerarSemana(Path)` (`AgendaServiceTest` com 23 cenários e `YamlAgendaRepositoryTest` com 8 cenários) | `gerarSemana(Path)` carrega a agenda uma única vez via `expandir(Path)` e reutiliza `AgendaExpandida` internamente para calcular intervalos livres. Usa `IsoFields.WEEK_BASED_YEAR` e `IsoFields.WEEK_OF_WEEK_BASED_YEAR`, valida `BlocoManual` sobrepostos no mesmo dia (via Bean Validation em `AgendaDto` e guarda em `AgendaService`), separa `TipoCompromisso.PLANTAO` por enum + `fatiarPorDia()` protegendo ocupações não-`PLANTAO` coincidentes, recorta `BlocoManual` à jornada e às ocupações não-`PLANTAO` emitindo avisos, classifica intervalos livres com `Classificacao` descartando `< interrompivelMinimoMin`, aplica precedência manual (total e parcial) sobre `PLANTAO` gerando `VIGILANCIA` nas fatias restantes e ordena `Semana.blocos()` por `(data, inicio, fim, tipo, descricao)`. `.\mvnw.cmd -B clean verify` verde (`57/57` testes, `4/4` ArchUnit); JaCoCo: `dominio` `100%` linhas (`98/98`) e instruções (`506/506`); `agenda` `98,8%` linhas (`599/606`) e `99,2%` instruções (`2750/2773`). Próxima tarefa: `2.6` |
| 2026-11-21 | Fase 2 | Commit da Tarefa 2.5 (`e037215`) publicado em `origin/dev`; Tarefa 2.6 concluída com `AgendaCasosDeBordaTest` (7 cenários end-to-end) e **Gate 2 aprovado** (JaCoCo `check` independente por pacote em `pom.xml` + ArchUnit estrito) | Cobre end-to-end (`YamlAgendaRepository` + `AgendaService.gerarSemana`): margens sobrepostas (`10:00 + 15m` e `10:30 - 30m` → ocupação contínua `10:00–10:30` sem duplicação), compromissos colados, dia totalmente ocupado, dia vazio (`07:00–23:00 PROFUNDO`), compromisso após as 22h recortado à jornada, compromisso cruzando meia-noite (fatiado em dois dias e descarte domingo→segunda com aviso) e semana com dia sem academia (sexta contínua `16:45–23:00`). Conferência manual × serviço em `exemplos/agenda.exemplo.yaml` (semana `2026-W48`): segunda `23/11` (`07:00–08:30 PROFUNDO`, `11:30–12:00 INTERROMPIVEL`, `13:00–15:30 PROFUNDO`, `17:45–18:00` descartado, `19:30–23:00 PROFUNDO`); quarta `25/11` (`07:00–08:00 INTERROMPIVEL`, `09:15–10:30 INTERROMPIVEL`, `13:30–15:00 PROFUNDO`, `15:00–17:00 VIGILANCIA` manual, `17:00–23:00 PROFUNDO`); sexta `27/11` (`07:00–10:00 PROFUNDO`, `10:00–11:30 PROFUNDO` manual, `11:30–12:00 INTERROMPIVEL`, `13:00–14:30 PROFUNDO`, `16:45–23:00 PROFUNDO`); domingo `29/11` (`07:00–23:00 PROFUNDO`). `pom.xml` valida mínimo independente `0.80` (`LINE` e `INSTRUCTION`) em `dev.cadencia.dominio` (`100,0%` / `100,0%`) e `dev.cadencia.agenda` (`98,8%` / `99,2%`). `ArquiteturaTest` estrito (`allowEmptyShould(true)` removido de 3 regras e mantido exclusivamente em `cliNaoAcessaAdaptersNemArquivos` até o Gate 3 / Tarefa 3.2). `.\mvnw.cmd -B clean verify` verde (`64/64` testes). Próxima tarefa: `3.1` (Fase 2B) |
| 2026-11-21 | Fase 2B | Commit da Tarefa 2.6 + Gate 2 (`47bc8cb`) publicado em `origin/dev`; Tarefa 3.1 concluída com `SemanaPlanejada.intervalosOcupados`, `ExportadorStrategy`, `MarkdownSemanalExportador` e `MarkdownSemanalExportadorTest` (3 cenários) | `SemanaPlanejada` passa a expor `List<IntervaloOcupado> intervalosOcupados` imutável (preservando construtor de conveniência de 3 argumentos); `AgendaService.gerarSemana(Path)` preenche `intervalosOcupados` via marcação interna em `expandirInterno(Path)` pelo enum original `TipoCompromisso.PLANTAO` (sem busca textual em descrição), excluindo todos os fragmentos de `PLANTAO` inclusive quando cruzam a meia-noite para evitar duplicidade com blocos `VIGILANCIA`. `ExportadorStrategy` define `exportar(SemanaPlanejada)` e `MarkdownSemanalExportador` gera o Markdown semanal puro em memória conforme `@dev/DESIGN.md` (cabeçalho `# Semana AAAA-Wnn`, `## Resumo`, 7 tabelas diárias em ordem cronológica combinando ocupações e blocos livres, `## Checklist de estudos`, `## Avisos`), determinístico, idempotente, com quebras `LF` (`\n`), sanitização de pipes/quebras em células e ordenação com suporte a términos em `00:00`. `.\mvnw.cmd -B clean verify` verde (`68/68` testes, `4/4` ArchUnit); JaCoCo: `dominio` `100,0%` linhas (`98/98`) e instruções (`506/506`), `agenda` `98,9%` linhas (`619/626`) e `99,3%` instruções (`2849/2868`), `exportacao.strategies` `100,0%` linhas (`191/191`) e `99,8%` instruções (`805/807`). Próxima tarefa: `3.2` |
| 2026-11-21 | Fase 2B | Commit da Tarefa 3.1 (`131d963`) publicado em `origin/dev`; Tarefa 3.2 concluída com sobrecarga `AgendaService.gerarSemana(Path, LocalDate)`, `PlanoSemanalRepository`, `MarkdownPlanoSemanalRepository`, `PlanoSemanalService`, `PlanoSemanalGerado`, `CadenciaConfig`, `CadenciaApplication` + `dev.cadencia.cli` (`CadenciaCommand`, `SemanaCommand`, `PicocliSpringFactory`, `PicocliCommandRunner`) e suíte completa de testes | `AgendaService.gerarSemana(Path)` delega para `gerarSemana(Path, LocalDate)` usando `Clock` em `America/Sao_Paulo`; com data explícita, ignora `Clock` e calcula semana ISO (`WEEK_BASED_YEAR`/`WEEK_OF_WEEK_BASED_YEAR`), incluindo viradas de ano civil (`2025-12-31` → `2026-W01`, `2027-01-01` → `2026-W53`). Todo I/O e criação automática de diretórios pais em `<cadencia.diretorio-dados>/semana/AAAA-Wnn.md` fica isolado em `MarkdownPlanoSemanalRepository` (UTF-8, LF `\n`, sobrescrita sem duplicação), mantendo `dev.cadencia.cli` estritamente sem I/O ou `*Repository` (`ArquiteturaTest` estrito sem `allowEmptyShould(true)` nas 4 regras). Integração Spring Boot + Picocli via `PicocliSpringFactory` (`IFactory`), `PicocliCommandRunner` (`CommandLineRunner` + `ExitCodeGenerator`) e `SpringApplication.exit(...)` no `main`: sem subcomando imprime ajuda com código `0` sem efeitos colaterais; dados inválidos/YAML/agenda ausente/`--data` malformada retornam `2` sem stack trace; erro inesperado retorna `1` com stack trace apenas sob `--verbose`. `.\mvnw.cmd -B clean verify` verde (`81/81` testes, `4/4` ArchUnit); JaCoCo: `dominio` `100,0%` (`98/98`), `agenda` `98,9%` (`623/630`), `exportacao.strategies` `100,0%` (`191/191`), `exportacao` `97,1%` (`34/35`), `cli` `95,1%` (`77/81`), `dev.cadencia` `83,3%` (`10/12`). Próxima tarefa: `3.3` |
