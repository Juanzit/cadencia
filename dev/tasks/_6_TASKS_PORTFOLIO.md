# Plano de Implementação – Fase 7

**Cadência – Polimento de Portfólio e Release v0.1.0**

## ⚠️ Instruções Obrigatórias para Execução (LLM)

1. Antes de executar qualquer tarefa, leia `@dev/RULES.md`, `@dev/PRD.md`, `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md` e `@dev/GEMINI.md`.
2. Pré-requisito: Gate 7 de `dev/tasks/_5_TASKS_IA_LOCAL.md` aprovado.
3. **Segurança antes de publicar:** conferir que nenhum dado pessoal ou material de empresa existe no repositório **nem no histórico do Git**.
4. Atualize status, contadores e `@dev/GEMINI.md` ao concluir.

## 📊 Métricas e Progresso Geral

* **Total de Tarefas:** 6
* **Concluídas:** 0 (0%)
* **Em Andamento:** 0 (0%)
* **Pendentes:** 6 (100%)

---

## 🏁 Fase 7: Release e Portfólio

| # | Tarefa | Camada / Área | Prioridade | Status |
| :--- | :--- | :--- | :--- | :--- |
| 1.1 | Auditar o repositório e o histórico do Git em busca de dados pessoais, nomes, horários reais ou materiais de empresas; limpar o histórico se necessário antes de tornar público | Segurança | Crítica | 🔴 Não Iniciado |
| 1.2 | Escrever o `README.md` final: problema, funcionalidades, arquitetura (diagrama do fluxo), como rodar, exemplo de saída anonimizada e decisões principais | Documentação | Crítica | 🔴 Não Iniciado |
| 1.3 | Publicar relatório de cobertura (JaCoCo) e badges de build e cobertura no README | DevOps | Alta | 🔴 Não Iniciado |
| 1.4 | Gerar o executável (`jar`) com instruções de uso e criar a *release* `v0.1.0` no GitHub com notas de versão | DevOps | Alta | 🔴 Não Iniciado |
| 1.5 | Revisar os ADRs do `@dev/GEMINI.md` e criar uma seção "Decisões de projeto" legível para recrutadores | Documentação | Média | 🔴 Não Iniciado |
| 1.6 | Escrever o roteiro do próximo ciclo (backlog público de evolução: persistência JPA, API REST, exportação `.ics`) como *issues* no GitHub | Planejamento | Média | 🔴 Não Iniciado |

### ✅ Gate 8 — Pronto para Publicar
- [ ] Auditoria de dados pessoais concluída e registrada
- [ ] Um desconhecido consegue clonar, compilar e gerar um plano com os exemplos em menos de 10 minutos
- [ ] CI verde e cobertura visível
- [ ] Release `v0.1.0` publicada

---

## 🔭 Próximo Ciclo (fora do MVP, só após o Gate 8)

| Evolução | Tipo | Observação |
| :--- | :--- | :--- |
| Adapter JPA (SQLite/PostgreSQL) | Persistência | Implementa as mesmas interfaces `*Repository`; ótimo exercício de JPA |
| API REST | Backend | Novos controllers chamando os mesmos services |
| Exportação `.ics` | Strategy | Novo `ExportadorStrategy` para Google Calendar |
| Interface web | Frontend | Consome a API REST; identidade visual em novo ADR |
