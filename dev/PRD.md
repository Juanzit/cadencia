# Documento de Requisitos do Produto (PRD)

**Cadência – Planejador Semanal Local para Professor e Estudante de Backend Java**

| Campo | Valor |
| :--- | :--- |
| **Versão** | 0.1.0-MVP |
| **Data** | Novembro de 2026 |
| **Status** | Em Planejamento |

---

## 1. Visão Geral do Produto
O **Cadência** é uma aplicação em Java, executada localmente, que elimina o trabalho semanal de se reorganizar. Ele lê a agenda fixa, calcula os blocos livres, classifica a qualidade de cada bloco, distribui tarefas de estudo, prevê semanas livres das turmas e, com um modelo de IA local (Ollama), gera roteiros de aula e o resumo semanal.

## 2. O Problema (em ordem de prioridade)
1. **Reorganizar a semana inteira** antes das aulas, todo domingo. *(Problema 1: resolvido pelas Fases 1 e 2)*
2. **Pesquisar o que estudar** a cada tempo livre. *(Problema 2: Fase 3)*
3. **Não ter feedback** sobre quais horários realmente funcionam em casa. *(Problema 3: Fase 4)*
4. **Não saber quando uma turma muda de módulo** e a semana fica livre. *(Problema 4: Fase 5)*
5. **Montar roteiros de aula** a partir de slides ou do portal. *(Problema 5: Fase 6)*

## 3. Objetivos Principais
* Gerar, com um único comando, o plano completo da semana em Markdown.
* Funcionar sem internet e sem enviar dados pessoais ou materiais de empresas a terceiros.
* Manter a lógica de planejamento determinística e testável, sem depender de IA.
* Servir como projeto de portfólio de Backend Java (arquitetura em camadas, testes, CI).

## 4. Perfil de Uso
* **Usuário único:** o autor (professor em mais de uma empresa, estudante de Backend Java, com treino e rotina doméstica compartilhada). Sem login, sem multiusuário.

## 5. Requisitos Funcionais

| ID | Requisito | Fase |
| :--- | :--- | :---: |
| RF-01 | Ler a agenda fixa (aulas, reuniões, almoço, deslocamentos) de `agenda.yaml` com validação | 1–2 |
| RF-02 | Aplicar margens configuráveis (EmpresaA, preparo e feedback EmpresaB, deslocamento) | 2 |
| RF-03 | Calcular os intervalos livres da semana | 2 |
| RF-04 | Classificar cada bloco livre em `PROFUNDO`, `INTERROMPIVEL` ou `VIGILANCIA` | 2 |
| RF-05 | Gerar a tabela semanal em Markdown (`cadencia semana`) | 2 |
| RF-06 | Ler o backlog de estudos e alocar tarefas em blocos compatíveis, em pedaços retomáveis de 25 min | 3 |
| RF-07 | Garantir folga mínima entre blocos para absorver imprevistos | 3 |
| RF-08 | Registrar feedback semanal por bloco (`CONCLUIDO`, `INTERROMPIDO`, `NAO_ACONTECEU`) e nota "parei aqui" | 4 |
| RF-09 | Calcular a confiabilidade por faixa de horário e usá-la na alocação | 4 |
| RF-10 | Prever semanas de transição de módulo a partir de `turmas.csv` | 5 |
| RF-11 | Gerar resumo semanal com LLM local | 6 |
| RF-12 | Extrair texto de PDF e gerar roteiro por slide com LLM local | 6 |
| RF-13 | Funcionar normalmente se o Ollama estiver indisponível (só os recursos de texto falham) | 6 |

## 6. Requisitos Não Funcionais
* **Privacidade:** dados pessoais em `dados/` (fora do Git); repositório só com exemplos sintéticos.
* **Testabilidade:** cobertura mínima de 80% nas camadas `domain` e `service`.
* **Determinismo:** mesma entrada, mesma saída (relógio injetado, sem aleatoriedade).
* **Recursos:** operar com 32 GB de RAM e RTX 3050; o planejador não usa GPU.
* **Legibilidade:** toda saída é Markdown legível e editável à mão.

## 7. Fora do Escopo
* Interface web, contas, nuvem, multiusuário (candidatos a evolução futura, ver `dev/ARCHITECTURE.md`).
* Docker, n8n, MCP e Craft Anything.
* Leitura automática de capturas de tela (extração mensal manual para `turmas.csv`).
* Emissão de notas fiscais e envio automático de relatórios.

## 8. Fases e Gates de Verificação

Cada fase só avança quando o **gate** do seu arquivo de tarefas for aprovado.

| Fase | Arquivo | Entrega | Gate (resumo) |
| :---: | :--- | :--- | :--- |
| 1–2 | `dev/tasks/_1_TASKS_SETUP.md` | Setup + blocos livres da semana | `cadencia semana` gera tabela correta para a agenda de exemplo |
| 3 | `dev/tasks/_2_TASKS_ALOCACAO_ESTUDOS.md` | Estudos alocados nos blocos | Nenhuma tarefa em bloco incompatível; pedaços de 25 min |
| 4 | `dev/tasks/_3_TASKS_FEEDBACK_CONFIABILIDADE.md` | Feedback e aprendizado | Horário ruim deixa de receber trabalho profundo |
| 5 | `dev/tasks/_4_TASKS_TURMAS.md` | Previsão de turmas | Semana de transição aparece no plano |
| 6 | `dev/tasks/_5_TASKS_IA_LOCAL.md` | Roteiros e resumo com Ollama | Funciona com e sem Ollama |
| 7 | `dev/tasks/_6_TASKS_PORTFOLIO.md` | Release e polimento | `v0.1.0` publicada, sem dado pessoal no histórico |

## 9. Métricas de Sucesso
* **Principal:** rodar `cadencia semana` no domingo e receber o plano completo sem editar nada à mão, exceto o feedback.
* Tempo de reorganização semanal: de ~1 hora para menos de 10 minutos.
* Taxa de blocos `CONCLUIDO` por semana (acompanhada pelo próprio feedback).
