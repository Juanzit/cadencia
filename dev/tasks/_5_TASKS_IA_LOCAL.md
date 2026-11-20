# Plano de Implementação – Fase 6

**Cadência – IA Local (Ollama): Resumo Semanal, Roteiros e Planos de Aula (resolve o Problema 5: montar materiais de aula)**

## ⚠️ Instruções Obrigatórias para Execução (LLM)

1. Antes de executar qualquer tarefa, leia `@dev/RULES.md`, `@dev/PRD.md`, `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md` e `@dev/GEMINI.md`.
2. Pré-requisito: Gate 6 de `dev/tasks/_4_TASKS_TURMAS.md` aprovado.
3. **A IA só gera texto.** Nenhuma regra de planejamento passa a depender dela (ADR-001).
4. Se o Ollama estiver indisponível, o plano continua sendo gerado (RF-13).
5. Materiais de empresas ficam em `dados/` e nunca vão ao Git.
6. Atualize status, contadores e `@dev/GEMINI.md` ao concluir.

## 📊 Métricas e Progresso Geral

* **Total de Tarefas:** 10
* **Concluídas:** 0 (0%)
* **Em Andamento:** 0 (0%)
* **Pendentes:** 10 (100%)

---

## 🤖 Fase 6: Geração de Texto com Modelo Local

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 1.1 | Definir a interface `GeradorTexto` e a implementação `FakeGeradorTexto` para testes | Strategy | Crítica | 🔴 Não Iniciado |
| 1.2 | Implementar `OllamaGeradorTexto` (HTTP em `localhost:11434`, modelo e timeout configuráveis, sem streaming no MVP) | Strategy / Integração | Crítica | 🔴 Não Iniciado |
| 1.3 | Implementar o tratamento de indisponibilidade: aviso claro, saída parcial marcada como "texto não gerado", exit code `0` | Service / CLI | Crítica | 🔴 Não Iniciado |
| 1.4 | Testar na máquina (32 GB RAM, RTX 3050) 2 ou 3 modelos de 7–8B em português e registrar no `@dev/GEMINI.md` o modelo escolhido, a VRAM (Q1) e o tempo médio de resposta | Pesquisa / Validação | Alta | 🔴 Não Iniciado |
| 1.5 | Implementar o resumo semanal: entrada = feedback da semana; saída = resumo curto e sugestões de ajuste, anexado ao próximo plano | Service | Alta | 🔴 Não Iniciado |
| 1.6 | Implementar `PdfExtrator` (Apache PDFBox) extraindo o texto página a página | Adapter | Alta | 🔴 Não Iniciado |
| 1.7 | Implementar `RoteiroService`: para cada slide, gerar roteiro (o que falar, perguntas para a turma, tempo estimado, dúvidas prováveis) em `dados/roteiros/<empresa>/<tema>.md` | Service | Alta | 🔴 Não Iniciado |
| 1.8 | Criar o modelo fixo de **plano de aula padronizado** (EmpresaA) e definir o extrator conforme o formato do material (Q2: PDF, PPTX ou portal) | Service / Pesquisa | Média | 🔴 Não Iniciado |
| 1.9 | Implementar os comandos `cadencia resumo AAAA-Wnn` e `cadencia roteiro <arquivo.pdf> --empresa empresab` | CLI | Alta | 🔴 Não Iniciado |
| 1.10 | Escrever testes com `FakeGeradorTexto` (prompt montado corretamente, falha tratada, arquivo gerado) e um teste de integração opcional com Ollama real, excluído do CI | Testes | Crítica | 🔴 Não Iniciado |

### ✅ Gate 7 — IA Útil e Segura
- [ ] Com Ollama ligado: o roteiro de um PDF real de aula é gerado e é aproveitável com poucas edições
- [ ] Com Ollama desligado: `cadencia semana` continua funcionando sem erro
- [ ] Nenhum material de empresa aparece em `git status`
- [ ] Modelo escolhido e desempenho registrados no `@dev/GEMINI.md`
- [ ] Teste real: prepare uma aula da semana usando o roteiro gerado
