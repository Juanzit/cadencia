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
├── exemplos/                     # agenda.exemplo.yaml, backlog.exemplo.yaml, feedback.exemplo.md e turmas sintéticos (versionados)
├── dados/                        # dados reais (.gitignore: dados/* e !dados/.gitkeep)
└── src/
    ├── main/java/dev/cadencia/
    │   ├── CadenciaApplication.java  # ponto de entrada Spring Boot (SpringApplication.exit + System.exit)
    │   ├── CadenciaConfig.java       # wiring Spring das portas, adapters, serviços e propriedades cadencia.* (`pedaco-padrao-min`, `folga-minima-min`, `confiabilidade.*`)
    │   ├── cli/                  # CONTROLLER: comandos Picocli + integração Spring Boot
    │   │   ├── CadenciaCommand.java       # comando raiz (`semana`, `feedback`) sem efeitos colaterais sem subcomando
    │   │   ├── CliValidacoes.java         # validação compartilhada de `--data`, `--agenda` e `--backlog`
    │   │   ├── FeedbackCommand.java       # subcomando `feedback --modelo` com `--sobrescrever` e saída via FeedbackService (1.3)
    │   │   ├── PicocliCommandRunner.java  # CommandLineRunner + ExitCodeGenerator (exit codes 0/1/2 e --verbose)
    │   │   ├── PicocliSpringFactory.java  # CommandLine.IFactory integrado ao ApplicationContext
    │   │   └── SemanaCommand.java         # subcomando semana [--data AAAA-MM-DD] [--agenda caminho] [--backlog caminho] (1.6)
    │   ├── agenda/               # módulo de agenda e blocos livres
    │   │   ├── AgendaDto.java            # DTO + validação Bean Validation do YAML (incluindo blocos manuais sem sobreposição)
    │   │   ├── AgendaExpandida.java      # saída semanal imutável (ocupações, configuração e avisos)
    │   │   ├── AgendaIntervalosLivres.java # saída semanal imutável de janelas livres da jornada
    │   │   ├── AgendaRepository.java     # porta (retorna AgendaConfiguracao)
    │   │   ├── AgendaService.java        # expansão semanal, intervalos livres, classificação de blocos e sobrecarga por data (2.3–2.5, 3.2)
    │   │   ├── DadosInvalidosException.java # erros de leitura, sintaxe YAML ou validação
    │   │   ├── IntervaloLivre.java       # janela livre diária [inicio, fim) com duracao() positiva
    │   │   ├── IntervaloOcupado.java     # fragmento diário ocupado [inicio, fim)
    │   │   ├── SemanaPlanejada.java      # saída semanal imutável contendo inicioSemana, Semana ISO, intervalosOcupados, avisos e configuracao
    │   │   ├── TipoIntervaloOcupado.java # COMPROMISSO, MARGEM_ANTES, MARGEM_DEPOIS, DESLOCAMENTO_AUTOMATICO
    │   │   └── YamlAgendaRepository.java # adapter YAML (Jackson + Validator)
    │   ├── planejamento/         # módulo de alocação de estudos
    │   │   ├── BacklogDto.java   # DTO YAML do backlog + Bean Validation (1.1)
    │   │   ├── BacklogRepository.java # porta de leitura do backlog de estudos (1.1)
    │   │   ├── PedacoAlocado.java     # alocação de um Pedaco (blocoOrigem, data, inicio, fim, pedaco) (1.3)
    │   │   ├── ResultadoAlocacao.java # saída imutável (alocados e naoAlocados) (1.3)
    │   │   ├── YamlBacklogRepository.java # adapter YAML (Jackson + Bean Validation + IDs únicos + pedaco-padrao-min) (1.1, 1.8)
    │   │   ├── PlanejamentoService.java     # orquestra compatibilidade individual, folga mínima configurável e sobrecarga com confiabilidade (1.4–1.6, ADR-013)
    │   │   └── strategies/
    │   │       ├── AlocadorStrategy.java            # contrato Strategy determinístico de alocação com suporte opcional à confiabilidade (1.3, 1.6, ADR-013)
    │   │       ├── PrioridadeAlocadorStrategy.java  # menor prioridade primeiro preservando roadmap (1.3, ADR-013)
    │   │       └── ConfiabilidadeAlocadorStrategy.java # fábrica `comConfiabilidade` e proteção de blocos PROFUNDO degradados aceitando qualquer tarefa `INTERROMPIVEL` (1.6)
    │   ├── feedback/             # módulo de feedback e confiabilidade
    │   │   ├── FeedbackRepository.java          # porta de existência, leitura, histórico cronológico e gravação de feedback (1.1, 1.3)
    │   │   ├── FeedbackService.java             # gera modelo `feedback/AAAA-Wnn.md` (com confiabilidade) e resolve última nota `"parei aqui"` vigente por tarefa (1.3, 1.4, 1.6)
    │   │   ├── MarkdownFeedbackRepository.java  # parser tolerante (HH:mm e HH:mm–HH:mm) e escrita UTF-8 LF idempotente (1.1, 1.3)
    │   │   ├── ModeloFeedbackGerado.java        # resultado imutável da geração do arquivo modelo de feedback (1.3)
    │   │   └── ConfiabilidadeService.java       # `calcularPorFaixa` por faixa sobre janela de N semanas com mínimo de amostras e limite configurável (1.5)
    │   ├── turmas/               # módulo de turmas e previsão
    │   │   ├── TurmaRepository.java
    │   │   ├── CsvTurmaRepository.java
    │   │   └── PrevisaoTurmaService.java
    │   ├── ia/                   # módulo de IA local (Fase 6)
    │   │   ├── GeradorTexto.java
    │   │   ├── OllamaGeradorTexto.java
    │   │   ├── PdfExtrator.java
    │   │   └── RoteiroService.java
    │   ├── exportacao/           # saída Markdown e persistência do plano semanal
    │   │   ├── MarkdownPlanoSemanalRepository.java # adapter de gravação UTF-8 + LF criando diretórios pais
    │   │   ├── PlanoSemanalGerado.java             # resultado imutável (arquivoSaida + SemanaPlanejada + ResultadoAlocacao opcional)
    │   │   ├── PlanoSemanalRepository.java         # porta de persistência do plano semanal
    │   │   ├── PlanoSemanalService.java            # orquestra AgendaService, BacklogRepository, PlanejamentoService, FeedbackService, ConfiabilidadeService, ExportadorStrategy e PlanoSemanalRepository (3.2, 1.4, 1.6)
    │   │   └── strategies/
    │   │       ├── ExportadorStrategy.java          # interface Strategy de exportação (ResultadoAlocacao, notas de retomada e faixas de baixa confiabilidade) (3.1, 1.4, 1.6)
    │   │       └── MarkdownSemanalExportador.java   # geração determinística e idempotente do Markdown semanal com pedaços alocados, retomada `"parei aqui"` e avisos de confiabilidade (3.1, 1.4, 1.6)
    │   └── dominio/              # DOMAIN: records e regras puras (sem Spring)
    │       ├── AgendaConfiguracao.java
    │       ├── Bloco.java
    │       ├── BlocoManual.java
    │       ├── Classificacao.java
    │       ├── Compromisso.java
    │       ├── ConfiabilidadeFaixa.java
    │       ├── FaixaHorario.java
    │       ├── FeedbackSemanal.java
    │       ├── ItemFeedback.java
    │       ├── Jornada.java
    │       ├── MargemEmpresa.java
    │       ├── Margens.java
    │       ├── Pedaco.java
    │       ├── PeriodoDia.java
    │       ├── Semana.java
    │       ├── StatusBloco.java
    │       ├── Tarefa.java
    │       ├── TipoBloco.java
    │       ├── TipoCompromisso.java
    │       └── Turma.java
    └── test/java/dev/cadencia/   # espelha a estrutura de main (inclui AgendaCasosDeBordaTest, AlocacaoCasosDeBordaTest, FeedbackConfiabilidadeCasosDeBordaTest, ConfiabilidadeServiceTest, ConfiabilidadeAlocadorStrategyTest e SemanaCommandIntegrationTest) + ArquiteturaTest (ArchUnit estrito nas 5 regras, incluindo planejamento -> feedback proibido)
```

> `groupId` definido: `dev.cadencia` (Q4 decidida).

## 2. Fluxo de Dados

```text
agenda.yaml ──> AgendaRepository ──> AgendaService ──> SemanaPlanejada (Semana + ocupações + avisos) ─┐
backlog.yaml ─> BacklogRepository ─> PlanejamentoService (AlocadorStrategy) <───────┤
feedback/*.md ─> FeedbackRepository ─> ConfiabilidadeService ───────────────────────┤
turmas.csv ───> TurmaRepository ───> PrevisaoTurmaService ──────────────────────────┤
                                                                                    v
                     PlanoSemanalService (ExportadorStrategy + PlanoSemanalRepository) ──> semana/AAAA-Wnn.md
(Fase 6)  PDF ──> PdfExtrator ──> RoteiroService ──> GeradorTexto (Ollama) ──> roteiros/*.md
```

## 3. Modelagem de Domínio

* `Compromisso`: `diaSemana`, `inicio`, `duracao` (`fim()` derivado), `TipoCompromisso` (`AULA`, `REUNIAO`, `ALMOCO`, `DESLOCAMENTO`, `TREINO`, `PLANTAO`), `empresa`, `local` e `titulo` opcionais (ADR-010).
* `Bloco`: data, início, fim, `TipoBloco` (`PROFUNDO`, `INTERROMPIVEL`, `VIGILANCIA`), `descricao` opcional.
* `AgendaConfiguracao`: configuração completa imutável da agenda (`Jornada`, `Margens` com `MargemEmpresa` por empresa, `Classificacao`, lista de `Compromisso` e lista de `BlocoManual`).
* `Tarefa`: id, título, duração total, tipos aceitos, tamanho do pedaço, prioridade.
* `Pedaco`: referência imutável à `Tarefa`, `numero`, `total` e duração positiva da fração (`numero/total`).
* `StatusBloco`: enum determinístico (`CONCLUIDO` = `1.0`, `INTERROMPIDO` = `0.5`, `NAO_ACONTECEU` = `0.0`).
* `PeriodoDia` e `FaixaHorario`: agrupamento estatístico determinístico por `(DayOfWeek, PeriodoDia)` (`MANHA < 12:00`, `TARDE 12:00–17:59`, `NOITE >= 18:00`).
* `ConfiabilidadeFaixa`: record imutável de 5 componentes (`faixa`, `amostras`, `indice`, `amostrasSuficientes`, `baixaConfiabilidade`), garantindo consistência (`baixaConfiabilidade` só pode ser `true` quando `amostrasSuficientes` for `true`).
* `ItemFeedback`: registro imutável por dia/horário (com `fim` opcional e nota normalizada `"parei aqui"`), status e derivação de `FaixaHorario`.
* `FeedbackSemanal`: feedback consolidado de uma semana ISO (`ano >= 2000`, `1 <= numeroIso <= 53`, listas imutáveis de itens e notas gerais).
* `Turma`: código, empresa, módulo atual, início do módulo, duração em semanas.
* `Semana`: ano, número ISO, lista de compromissos e blocos.

## 4. Strategy Pattern (pontos de extensão)

| Interface | Implementações | Quando troca |
| :--- | :--- | :--- |
| `AlocadorStrategy` | `PrioridadeAlocadorStrategy` (Fase 3, `ADR-013`), `ConfiabilidadeAlocadorStrategy` (Fase 4) | Evoluir a heurística sem tocar no serviço |
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
  confiabilidade:
    janela-semanas: 4
    minimo-amostras: 3
    limite-confiabilidade: 0.50
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
