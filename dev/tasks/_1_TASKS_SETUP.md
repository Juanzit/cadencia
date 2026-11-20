# Plano de Implementação – Fases 1 e 2

**Cadência – Setup do Projeto e Núcleo de Agenda (resolve o Problema 1: reorganizar a semana)**

## ⚠️ Instruções Obrigatórias para Execução (LLM)

1. Antes de executar qualquer tarefa abaixo, leia obrigatoriamente `@dev/RULES.md`, `@dev/PRD.md`, `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md` e `@dev/GEMINI.md`.
2. **Proteção do `.gitignore`:** não remova entradas existentes; apenas acrescente.
3. Execute as tarefas sequencialmente, respeitando **Camadas, Repository, Service Layer, DTO e Strategy**.
4. À medida que iniciar e concluir cada tarefa, atualize o status (`🔴 Não Iniciado` → `🟡 Em Andamento` → `🟢 Concluído`) e os contadores.
5. Ao final de cada fase, execute o **Gate de Verificação** correspondente. Não avance se algum item falhar.
6. Ao finalizar, registre decisões no `@dev/GEMINI.md` e atualize o log de execução.

## 📊 Métricas e Progresso Geral

* **Total de Tarefas:** 14
* **Concluídas:** 6 (43%)
* **Em Andamento:** 0 (0%)
* **Pendentes:** 8 (57%)

---

## 🚀 Fase 1: Setup Inicial e Infraestrutura

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 1.1 | Criar o repositório Git com `.gitignore` (incluindo `dados/`, `.env`, `target/`, arquivos de IDE) e `README.md` inicial | Setup | Crítica | 🟢 Concluído |
| 1.2 | Inicializar projeto Maven (Java 21, Spring Boot, Picocli, Jackson YAML, JUnit 5, AssertJ, JaCoCo) com `com.tngtech.archunit:archunit-junit5` em escopo `test` | Setup | Crítica | 🟢 Concluído |
| 1.3 | Criar a estrutura de pacotes de `@dev/ARCHITECTURE.md` (`cli`, `agenda`, `planejamento`, `feedback`, `turmas`, `ia`, `exportacao`, `dominio`) com `package-info.java` descrevendo cada responsabilidade, e criar `ArquiteturaTest` (ArchUnit) validando: (a) `dominio` não depende de Spring, Jackson nem I/O (`java.io`, `java.nio.file`); (b) fronteiras `cli` → `service` → `repository` (CLI não acessa repositórios/adapters; services não dependem de `cli`). Na Fase 1 as regras usam `allowEmptyShould(true)` (pacotes ainda vazios); no Gate 2 o `allowEmptyShould(true)` é removido e a validação passa a ser estrita | Setup / Testes | Alta | 🟢 Concluído |
| 1.4 | Configurar GitHub Actions executando `mvn verify` a cada push e pull request | DevOps | Alta | 🟢 Concluído |
| 1.5 | Criar `exemplos/agenda.exemplo.yaml` (sintético, inspirado nas regras reais: margens EmpresaA, preparo/feedback EmpresaB, deslocamento) e pasta `dados/` vazia com `.gitkeep` versionado; no `.gitignore` usar `dados/*` e `!dados/.gitkeep` (o conteúdo é ignorado, o `.gitkeep` não) | Dados | Alta | 🟢 Concluído |

### ✅ Gate 1 — Infraestrutura
- [x] `mvn verify` passa em ambiente limpo
- [x] CI verde no GitHub
- [x] `git status` não mostra nada de `dados/`
- [x] README descreve como compilar e rodar

---

## 🧠 Fase 2: Núcleo de Agenda e Blocos Livres

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 2.1 | Implementar `dominio`: `Compromisso`, `Bloco`, `TipoBloco`, `Semana` como *records* imutáveis. `Compromisso` é modelado como `inicio` + `Duration` (duração > 0 e < 24h), e `fim` é derivado; assim compromissos que cruzam a meia-noite (ex.: 23:00–01:00) são válidos. Ao gerar a semana, um compromisso que cruza a meia-noite é **fatiado** em dois intervalos (até 24:00 do dia e de 00:00 no dia seguinte; no domingo, a fração da segunda-feira seguinte é descartada na semana atual e registrada como aviso) | Domain | Crítica | 🟢 Concluído |
| 2.2 | Implementar `AgendaDto` (Bean Validation), `AgendaRepository` e `YamlAgendaRepository`, com tratamento **separado** de erros (padrão `@dev/DESIGN.md`): (a) **erro de sintaxe YAML** → linha e coluna obtidas da `JsonLocation` da exceção do Jackson; (b) **erro de validação de campo** (Bean Validation) → caminho do campo no formato `compromissos[i].campo` (a partir do `propertyPath` da violação), sem número de linha | Repository / DTO | Crítica | 🔴 Não Iniciado |
| 2.3 | Implementar em `AgendaService` a aplicação das margens configuráveis: 30 min antes da aula EmpresaA; 30 min antes e 15 min depois da aula EmpresaB; deslocamento de 30 min entre locais | Service | Crítica | 🔴 Não Iniciado |
| 2.4 | Implementar o cálculo dos intervalos livres da semana (subtração de compromissos e margens, mesclando sobreposições) com `Clock` injetado | Service | Crítica | 🔴 Não Iniciado |
| 2.5 | Implementar a classificação dos blocos livres em `PROFUNDO`, `INTERROMPIVEL` e `VIGILANCIA` (por regra configurável em `agenda.yaml`, incluindo `blocos_manuais`; AE vazia e plantão entram como `VIGILANCIA`) | Service | Alta | 🔴 Não Iniciado |
| 2.6 | Escrever testes dos casos de borda: margens sobrepostas, compromisso colado em outro, dia totalmente ocupado, dia vazio, compromisso após as 22h, compromisso que cruza a meia-noite (fatiado em dois intervalos, ocupando o fim de um dia e o início do seguinte), semana com dia sem academia | Testes | Crítica | 🔴 Não Iniciado |

### ✅ Gate 2 — Núcleo de Agenda
- [ ] Todos os testes de `dominio` e `agenda` passam
- [ ] Cobertura ≥ 80% em `dominio` e `agenda`
- [ ] Para `exemplos/agenda.exemplo.yaml`, os blocos calculados à mão batem com a saída do serviço (conferir ao menos 3 dias)
- [ ] YAML com erro de sintaxe gera erro com arquivo, linha e coluna; YAML com campo inválido gera erro com arquivo e caminho `compromissos[i].campo`
- [ ] `ArquiteturaTest` passa **sem** `allowEmptyShould(true)` (validação estrita)

---

## 🖥️ Fase 2B: Saída Markdown e Comando Principal

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 3.1 | Implementar `ExportadorStrategy` e `MarkdownSemanalExportador` gerando o plano no formato do `@dev/DESIGN.md` (resumo + tabela por dia), de forma idempotente | Strategy / Exportação | Crítica | 🔴 Não Iniciado |
| 3.2 | Implementar o comando `cadencia semana [--data AAAA-MM-DD] [--agenda caminho]` escrevendo em `dados/semana/AAAA-Wnn.md` | CLI | Crítica | 🔴 Não Iniciado |
| 3.3 | Atualizar `README.md` com instruções de uso e um exemplo real (anonimizado) da saída; marcar o Marco 1 como concluído e registrar no `@dev/GEMINI.md` | Documentação | Alta | 🔴 Não Iniciado |

### ✅ Gate 3 — Problema 1 Resolvido (critério de aceite do projeto)
- [ ] `cadencia semana` gera o arquivo da semana a partir da agenda de exemplo
- [ ] Rodar duas vezes gera arquivos idênticos
- [ ] Abrir o `.md` no AppFlowy mostra tabelas e checklist corretamente
- [ ] **Teste real:** rodar com sua agenda real (em `dados/`) e conferir se os blocos livres batem com a sua semana de verdade
- [ ] Ajustes das regras reais registrados (Q3 e Q5 do `@dev/GEMINI.md` respondidas ou mantidas em aberto)

> Só avance para `dev/tasks/_2_TASKS_ALOCACAO_ESTUDOS.md` quando todos os gates acima estiverem aprovados.
