# Arquitetura do Sistema – Cadência

Este documento detalha a arquitetura técnica baseada em **Java 21, Spring Boot, Picocli, Jackson (YAML) e JUnit 5**, aplicando **Camadas, Repository, Service Layer e Strategy**, com foco em permitir evolução (CLI → API REST, arquivos → banco de dados) sem reescrever o núcleo.

---

## 1. Estrutura de Pastas e Camadas

```text
cadencia/
├── README.md                     # apresentação do projeto
├── GEMINI.md                     # ponteiro curto para dev/
├── dev/                          # documentação de desenvolvimento
│   ├── README.md                 # índice interno
│   ├── PRD.md · RULES.md · ARCHITECTURE.md · DESIGN.md · GEMINI.md
│   └── tasks/                    # _1_TASKS_SETUP.md … _6_TASKS_PORTFOLIO.md
├── pom.xml
├── exemplos/                     # agenda, backlog, turmas e feedback sintéticos (versionados)
├── dados/                        # dados reais (.gitignore: dados/* e !dados/.gitkeep)
└── src/
    ├── main/java/dev/cadencia/
    │   ├── cli/                  # CONTROLLER: comandos Picocli
    │   ├── agenda/               # módulo de agenda e blocos livres
    │   │   ├── AgendaDto.java            # DTO + validação Bean Validation do YAML (incluindo blocos manuais sem sobreposição)
    │   │   ├── AgendaExpandida.java      # saída semanal imutável (ocupações, configuração e avisos)
    │   │   ├── AgendaIntervalosLivres.java # saída semanal imutável de janelas livres da jornada
    │   │   ├── AgendaRepository.java     # porta (retorna AgendaConfiguracao)
    │   │   ├── AgendaService.java        # expansão semanal, intervalos livres e classificação de blocos da Semana (2.3–2.5)
    │   │   ├── DadosInvalidosException.java # erros de leitura, sintaxe YAML ou validação
    │   │   ├── IntervaloLivre.java       # janela livre diária [inicio, fim) com duracao() positiva
    │   │   ├── IntervaloOcupado.java     # fragmento diário ocupado [inicio, fim)
    │   │   ├── SemanaPlanejada.java      # saída semanal imutável contendo inicioSemana, Semana ISO e avisos
    │   │   ├── TipoIntervaloOcupado.java # COMPROMISSO, MARGEM_ANTES, MARGEM_DEPOIS, DESLOCAMENTO_AUTOMATICO
    │   │   └── YamlAgendaRepository.java # adapter YAML (Jackson + Validator)
    │   ├── planejamento/         # módulo de alocação de estudos
    │   │   ├── BacklogDto.java
    │   │   ├── BacklogRepository.java
    │   │   ├── PlanejamentoService.java
    │   │   └── strategies/
    │   │       ├── AlocadorStrategy.java
    │   │       ├── PrioridadeAlocadorStrategy.java
    │   │       └── ConfiabilidadeAlocadorStrategy.java
    │   ├── feedback/             # módulo de feedback e confiabilidade
    │   │   ├── FeedbackRepository.java
    │   │   ├── MarkdownFeedbackRepository.java
    │   │   └── ConfiabilidadeService.java
    │   ├── turmas/               # módulo de turmas e previsão
    │   │   ├── TurmaRepository.java
    │   │   ├── CsvTurmaRepository.java
    │   │   └── PrevisaoTurmaService.java
    │   ├── ia/                   # módulo de IA local (Fase 6)
    │   │   ├── GeradorTexto.java
    │   │   ├── OllamaGeradorTexto.java
    │   │   ├── PdfExtrator.java
    │   │   └── RoteiroService.java
    │   ├── exportacao/           # saída Markdown
    │   │   └── strategies/
    │   │       ├── ExportadorStrategy.java
    │   │       └── MarkdownSemanalExportador.java
    │   └── dominio/              # DOMAIN: records e regras puras (sem Spring)
    │       ├── AgendaConfiguracao.java
    │       ├── Bloco.java
    │       ├── BlocoManual.java
    │       ├── Classificacao.java
    │       ├── Compromisso.java
    │       ├── Jornada.java
    │       ├── MargemEmpresa.java
    │       ├── Margens.java
    │       ├── Semana.java
    │       ├── Tarefa.java
    │       ├── TipoBloco.java
    │       ├── TipoCompromisso.java
    │       └── Turma.java
    └── test/java/dev/cadencia/   # espelha a estrutura de main (inclui AgendaCasosDeBordaTest) + ArquiteturaTest (ArchUnit estrito no Gate 2)
```

> `groupId` definido: `dev.cadencia` (Q4 decidida).

## 2. Fluxo de Dados

```text
agenda.yaml ──> AgendaRepository ──> AgendaService ──> SemanaPlanejada (Semana + blocos classificados + avisos) ─┐
backlog.yaml ─> BacklogRepository ─> PlanejamentoService (AlocadorStrategy) <───────┤
feedback/*.md ─> FeedbackRepository ─> ConfiabilidadeService ───────────────────────┤
turmas.csv ───> TurmaRepository ───> PrevisaoTurmaService ──────────────────────────┤
                                                                                    v
                                                 ExportadorStrategy ──> semana/AAAA-Wnn.md
(Fase 6)  PDF ──> PdfExtrator ──> RoteiroService ──> GeradorTexto (Ollama) ──> roteiros/*.md
```

## 3. Modelagem de Domínio

* `Compromisso`: `diaSemana`, `inicio`, `duracao` (`fim()` derivado), `TipoCompromisso` (`AULA`, `REUNIAO`, `ALMOCO`, `DESLOCAMENTO`, `TREINO`, `PLANTAO`), `empresa`, `local` e `titulo` opcionais (ADR-010).
* `Bloco`: data, início, fim, `TipoBloco` (`PROFUNDO`, `INTERROMPIVEL`, `VIGILANCIA`), `descricao` opcional.
* `AgendaConfiguracao`: configuração completa imutável da agenda (`Jornada`, `Margens` com `MargemEmpresa` por empresa, `Classificacao`, lista de `Compromisso` e lista de `BlocoManual`).
* `Tarefa`: id, título, duração total, tipos aceitos, tamanho do pedaço, prioridade.
* `Turma`: código, empresa, módulo atual, início do módulo, duração em semanas.
* `Semana`: ano, número ISO, lista de compromissos e blocos.

## 4. Strategy Pattern (pontos de extensão)

| Interface | Implementações | Quando troca |
| :--- | :--- | :--- |
| `AlocadorStrategy` | `PrioridadeAlocadorStrategy` (Fase 3), `ConfiabilidadeAlocadorStrategy` (Fase 4) | Evoluir a heurística sem tocar no serviço |
| `ExportadorStrategy` | `MarkdownSemanalExportador`; futuras: JSON, ICS (calendário) | Novos formatos de saída |
| `GeradorTexto` | `OllamaGeradorTexto`, `FakeGeradorTexto` (testes) | Trocar de modelo ou provedor |

## 5. Caminho de Escalabilidade

Cada degrau só é construído quando houver necessidade real:

1. **Hoje:** CLI + arquivos YAML/CSV/MD.
2. **Persistência:** novo adapter JPA (SQLite ou PostgreSQL) implementando as mesmas interfaces `*Repository`. Services e CLI não mudam.
3. **API REST:** novos controllers Spring MVC chamando os mesmos services. A CLI continua funcionando.
4. **Interface web:** consome a API REST.
5. **Exportação para calendário:** novo `ExportadorStrategy` gerando `.ics` (Google Calendar).
6. **Novos perfis:** múltiplos usuários exigiriam autenticação e dados por usuário, fora do escopo atual.

## 6. Configuração

`application.yaml` (valores padrão; sobrescritos por variáveis de ambiente):

```yaml
cadencia:
  diretorio-dados: ./dados
  fuso: America/Sao_Paulo
  pedaco-padrao-min: 25
  folga-minima-min: 10
  ollama:
    url: http://localhost:11434
    modelo-geral: <definir após testar na GPU>
    timeout-segundos: 120
```

## 7. Estratégia de Testes

* **Unitários:** `domain` e `service`, com `Clock` fixo e repositórios em memória.
* **Adapters:** testes com arquivos de `exemplos/` (e arquivos inválidos para validar mensagens de erro).
* **Strategies:** testes comparativos com o mesmo cenário.
* **IA:** testes com `FakeGeradorTexto`; teste de integração com Ollama real é opcional e marcado para não rodar no CI.
* **CI (GitHub Actions):** `mvn verify` a cada push.
