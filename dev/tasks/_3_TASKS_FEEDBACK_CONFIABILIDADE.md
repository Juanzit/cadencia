# Plano de Implementação – Fase 4

**Cadência – Feedback Semanal e Aprendizado de Horários (resolve o Problema 3: saber quais horários funcionam)**

## ⚠️ Instruções Obrigatórias para Execução (LLM)

1. Antes de executar qualquer tarefa, leia `@dev/RULES.md`, `@dev/PRD.md`, `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md` e `@dev/GEMINI.md`.
2. Pré-requisito: Gate 4 de `dev/tasks/_2_TASKS_ALOCACAO_ESTUDOS.md` aprovado.
3. A confiabilidade é **cálculo estatístico simples**, sem IA.
4. Atualize status, contadores e `@dev/GEMINI.md` ao concluir.

## 📊 Métricas e Progresso Geral

* **Total de Tarefas:** 8
* **Concluídas:** 6 (75%)
* **Em Andamento:** 0 (0%)
* **Pendentes:** 2 (25%)

---

## 🔁 Fase 4: Feedback e Confiabilidade

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 1.1 | Implementar `FeedbackRepository` e `MarkdownFeedbackRepository` (parser do formato do `@dev/DESIGN.md`) com mensagens de erro por linha | Repository / DTO | Crítica | 🟢 Concluído |
| 1.2 | Implementar `StatusBloco` (`CONCLUIDO`, `INTERROMPIDO`, `NAO_ACONTECEU`) e o registro da nota "parei aqui" | Domain | Crítica | 🟢 Concluído |
| 1.3 | Gerar o modelo de feedback da semana (`cadencia feedback --modelo`) já preenchido com os blocos do plano, para o usuário só marcar | CLI / Exportação | Alta | 🟢 Concluído |
| 1.4 | Implementar na semana seguinte o reaparecimento da nota "parei aqui" como contexto da tarefa retomada | Service | Alta | 🟢 Concluído |
| 1.5 | Implementar `ConfiabilidadeService`: índice por faixa de horário (dia da semana + período) com janela das últimas N semanas e mínimo de amostras antes de confiar no índice | Service | Crítica | 🟢 Concluído |
| 1.6 | Implementar `ConfiabilidadeAlocadorStrategy`: trabalho profundo só em faixas com confiabilidade acima do limite configurável; faixas ruins recebem tarefas interrompíveis | Strategy | Crítica | 🟢 Concluído |
| 1.7 | Escrever testes: poucas amostras, faixa que melhora com o tempo, nota de retomada, feedback inválido, semana sem feedback | Testes | Crítica | 🔴 Não Iniciado |
| 1.8 | Criar `exemplos/feedback.exemplo.md`, atualizar README e registrar ADR (janela, limite, mínimo de amostras) no `@dev/GEMINI.md` | Documentação | Média | 🔴 Não Iniciado |

### ✅ Gate 5 — O Sistema Aprende
- [ ] Com 3 ou mais semanas de feedback de exemplo, uma faixa ruim deixa de receber trabalho profundo
- [ ] Com poucas amostras, o sistema não decide (usa o comportamento padrão)
- [ ] A nota "parei aqui" reaparece na tarefa certa
- [ ] Teste real: duas semanas seguidas de uso com feedback real
- [ ] Cobertura ≥ 80% em `feedback`
