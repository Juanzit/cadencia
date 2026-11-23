# Plano de Implementação – Fase 5

**Cadência – Previsão de Turmas e Semanas Livres (resolve o Problema 4: saber quando muda de módulo)**

## ⚠️ Instruções Obrigatórias para Execução (LLM)

1. Antes de executar qualquer tarefa, leia `@dev/RULES.md`, `@dev/PRD.md`, `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md` e `@dev/GEMINI.md`.
2. Pré-requisito: Gate 5 de `dev/tasks/_3_TASKS_FEEDBACK_CONFIABILIDADE.md` aprovado.
3. A previsão é **aritmética de datas**, sem IA. A extração da captura mensal é manual (ADR-006).
4. Atualize status, contadores e `@dev/GEMINI.md` ao concluir.

## 📊 Métricas e Progresso Geral

* **Total de Tarefas:** 7
* **Concluídas:** 2 (29%)
* **Em Andamento:** 0 (0%)
* **Pendentes:** 5 (71%)

---

## 🗓️ Fase 5: Turmas e Transições de Módulo

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 1.1 | Implementar `TurmaDto`, `TurmaRepository` e `CsvTurmaRepository` lendo `turmas.csv` (turma, empresa, módulo atual, início do módulo, duração em semanas) com validação linha a linha | Repository / DTO | Crítica | 🟢 Concluído |
| 1.2 | Implementar `Turma` no domínio com cálculo do fim do módulo e do módulo seguinte | Domain | Crítica | 🟢 Concluído |
| 1.3 | Implementar `PrevisaoTurmaService`: marcar a **semana de transição** como possível semana livre, sinalizando que é previsão e não certeza | Service | Crítica | 🔴 Não Iniciado |
| 1.4 | Integrar a previsão ao plano semanal: seção "Turmas em transição" e aviso nas semanas afetadas | Exportação | Alta | 🔴 Não Iniciado |
| 1.5 | Implementar o comando `cadencia turmas [--semanas N]` listando as próximas transições | CLI | Alta | 🔴 Não Iniciado |
| 1.6 | Escrever testes: módulo que termina no domingo, duas turmas na mesma semana, virada de ano, duração zero (inválida), CSV com linha malformada | Testes | Crítica | 🔴 Não Iniciado |
| 1.7 | Criar `exemplos/turmas.exemplo.csv`, documentar o fluxo mensal de extração da captura (5 min) e registrar no `@dev/GEMINI.md` | Documentação | Média | 🔴 Não Iniciado |

### ✅ Gate 6 — Turmas Previstas
- [ ] Datas de fim e semanas de transição conferidas à mão para o CSV de exemplo
- [ ] A semana de transição aparece no plano semanal com aviso de "previsão"
- [ ] Teste real: previsão do próximo mês bate com o que acontece na EmpresaA
- [ ] Cobertura ≥ 80% em `turmas`
