# Plano de Implementação – Fase 3

**Cadência – Backlog de Estudos e Alocação nos Blocos (resolve o Problema 2: pesquisar o que estudar)**

## ⚠️ Instruções Obrigatórias para Execução (LLM)

1. Antes de executar qualquer tarefa, leia `@dev/RULES.md`, `@dev/PRD.md`, `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md` e `@dev/GEMINI.md`.
2. Pré-requisito: Gates 1, 2 e 3 de `dev/tasks/_1_TASKS_SETUP.md` aprovados.
3. Mantenha a regra: **alocação é código determinístico, sem IA**, via `AlocadorStrategy`.
4. Atualize status, contadores e `@dev/GEMINI.md` ao concluir.

## 📊 Métricas e Progresso Geral

* **Total de Tarefas:** 8
* **Concluídas:** 2 (25%)
* **Em Andamento:** 0 (0%)
* **Pendentes:** 6 (75%)

---

## 📚 Fase 3: Backlog e Alocação

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 1.1 | Implementar `BacklogDto`, `BacklogRepository` e `YamlBacklogRepository` lendo `backlog.yaml` (id, título, duração total, tipos aceitos, pedaço em minutos, prioridade) com validação | Repository / DTO | Crítica | 🟢 Concluído |
| 1.2 | Implementar `Tarefa` e `Pedaco` no domínio (duração total dividida em pedaços de 25 min por padrão, último pedaço pode ser menor) | Domain | Crítica | 🟢 Concluído |
| 1.3 | Definir a interface `AlocadorStrategy` e implementar `PrioridadeAlocadorStrategy` (menor número de prioridade primeiro, mantendo a ordem do roadmap) | Strategy | Crítica | 🔴 Não Iniciado |
| 1.4 | Garantir compatibilidade: um pedaço só entra em bloco cujo tipo esteja na lista `aceita` da tarefa (ex.: Spring Boot novo nunca em `VIGILANCIA`) | Service | Crítica | 🔴 Não Iniciado |
| 1.5 | Implementar folga mínima configurável entre blocos de estudo e treino/aulas, para absorver imprevistos | Service | Alta | 🔴 Não Iniciado |
| 1.6 | Estender o exportador para incluir "Checklist de estudos" e a coluna de blocos com a tarefa e o pedaço (ex.: `Java Collections (2/6)`) | Exportação | Alta | 🔴 Não Iniciado |
| 1.7 | Escrever testes: backlog vazio, tarefa maior que todos os blocos, nenhum bloco compatível, prioridades iguais, pedaço final menor | Testes | Crítica | 🔴 Não Iniciado |
| 1.8 | Criar `exemplos/backlog.exemplo.yaml` (roadmap Backend Java: Java core, Collections, Spring Boot, JPA, testes, SQL), atualizar README e registrar ADR da heurística no `@dev/GEMINI.md` | Documentação | Média | 🔴 Não Iniciado |

### ✅ Gate 4 — Estudos Alocados
- [ ] Nenhuma tarefa aparece em bloco de tipo incompatível
- [ ] Todos os pedaços respeitam o tamanho configurado
- [ ] Folga mínima respeitada entre blocos
- [ ] Teste real: o plano de uma semana sua não exige pesquisar o que estudar
- [ ] Cobertura ≥ 80% em `planejamento`
