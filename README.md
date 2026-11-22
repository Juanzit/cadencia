# Cadência

> Planejador semanal que roda 100% local: calcula seus blocos de tempo livre, distribui tarefas de estudo conforme a qualidade de cada bloco e usa um modelo local (Ollama) para gerar roteiros de aula e o feedback da semana.

**Status:** Marco 1 (Fases 1–2B, Gate 3) e **Marco 2 — v0.2** (Fase 3: `8/8` tarefas concluídas e **Gate 4 — Alocação de Estudos aprovado**) concluídos e publicados; próxima etapa: Fase 4 — Feedback e Confiabilidade (`dev/tasks/_3_TASKS_FEEDBACK_CONFIABILIDADE.md`).

## Stack

Java 21 · Spring Boot · Picocli · Jackson YAML · JUnit 5 · ArchUnit · JaCoCo · GitHub Actions · Ollama (Fase 6)

## Como compilar e testar

**Pré-requisito:** JDK Java 21 instalado.

**Linux/macOS:**

```bash
./mvnw clean verify
```

**Windows:**

```powershell
.\mvnw.cmd clean verify
```

## Como usar (`cadencia semana`)

Após compilar (`clean verify` ou `clean package`), execute o JAR passando `semana` e as opções opcionais `--data`, `--agenda` e `--backlog`:

**Linux/macOS:**

```bash
java -jar target/cadencia-0.1.0-SNAPSHOT.jar semana --agenda exemplos/agenda.exemplo.yaml --backlog exemplos/backlog.exemplo.yaml --data 2026-11-23
```

**Windows:**

```powershell
java -jar target\cadencia-0.1.0-SNAPSHOT.jar semana --agenda exemplos/agenda.exemplo.yaml --backlog exemplos/backlog.exemplo.yaml --data 2026-11-23
```

Ou via Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=semana --agenda exemplos/agenda.exemplo.yaml --backlog exemplos/backlog.exemplo.yaml --data 2026-11-23"
```

### Configurando seu backlog pessoal (`dados/backlog.yaml`)

1. Copie o backlog de exemplo para `dados/backlog.yaml` (ignorado pelo Git para manter seus dados fora do repositório):
   - **Linux/macOS:** `cp exemplos/backlog.exemplo.yaml dados/backlog.yaml`
   - **Windows (PowerShell/CMD):** `copy exemplos\backlog.exemplo.yaml dados\backlog.yaml`
2. Ao omitir `--backlog`, o Cadência **detecta automaticamente** `<cadencia.diretorio-dados>/backlog.yaml` (padrão `./dados/backlog.yaml`) quando o arquivo existe. Se não existir, o plano semanal continua sendo gerado normalmente apenas com os blocos livres.
3. Ao passar `--backlog <caminho>`, o arquivo informado tem precedência sobre o backlog padrão (e a ausência ou erro de validação retorna código `2`).

### Opções da CLI e saída

- **`--agenda <caminho>`** (opcional): substitui apenas o arquivo YAML da agenda. Quando omitido, lê `./dados/agenda.yaml` (ou `<cadencia.diretorio-dados>/agenda.yaml`).
- **`--backlog <caminho>`** (opcional): informa um arquivo YAML de backlog alternativo. Quando omitido, usa `./dados/backlog.yaml` (ou `<cadencia.diretorio-dados>/backlog.yaml`) caso exista.
- **`--data <AAAA-MM-DD>`** (opcional): seleciona a semana ISO correspondente à data informada (`AAAA-Wnn`). Quando omitido, usa a data atual no fuso `America/Sao_Paulo`.
- **`--verbose`** (opcional): exibe o stack trace completo em erros inesperados (código de saída `1`).
- **Arquivo gerado:** `<cadencia.diretorio-dados>/semana/AAAA-Wnn.md` (padrão `./dados/semana/AAAA-Wnn.md`), em UTF-8 com quebra de linha LF e sobrescrita idempotente.
- **Sobrescrever o diretório de dados via JVM** (coloque `-D` antes de `-jar`):

```powershell
java -Dcadencia.diretorio-dados=/caminho/temporario -jar target\cadencia-0.1.0-SNAPSHOT.jar semana --agenda exemplos/agenda.exemplo.yaml --backlog exemplos/backlog.exemplo.yaml --data 2026-11-23
```

- **Como interpretar as tarefas no Markdown (`Título (k/N)`):**
  - Cada tarefa é dividida de forma determinística em pedaços retomáveis (`25` min por padrão via `cadencia.pedaco-padrao-min`, ou o valor definido em `tamanho_pedaco_min` no YAML; o último pedaço absorve apenas o tempo restante).
  - Na tabela de cada dia e na seção `## Checklist de estudos`, cada pedaço alocado aparece no formato **`Título (k/N)`** (por exemplo, `Java Collections e Complexidade (1/6)`), indicando o `k`-ésimo pedaço de um total de `N`, sempre respeitando os `tipos_aceitos` da tarefa e a folga mínima configurável (`10` min via `cadencia.folga-minima-min`) junto a compromissos adjacentes.
  - Pedaços que não couberem por falta de tempo ou bloco compatível são listados em `## Avisos`.
- **Códigos de saída:** `0` (sucesso ou ajuda com `cadencia` sem subcomando), `2` (agenda/backlog ausente quando obrigatório, YAML inválido, falha de validação ou `--data` malformada), `1` (erro inesperado).

### Exemplo real da saída (anonimizado — `exemplos/agenda.exemplo.yaml` + `exemplos/backlog.exemplo.yaml`, semana `2026-W48`)

```markdown
# Semana 2026-W48 (23/11 a 29/11)

## Resumo
- Blocos livres: 24 (profundo: 18 · interrompível: 5 · vigilância: 1)
- Tempo livre total: 85h15
- Turmas em transição: nenhuma
- Pedaços alocados: 30 (12h00)

## Segunda-feira, 23/11
| Horário | Tipo | Detalhe |
| :--- | :--- | :--- |
| 07:00–07:25 | **Bloco profundo** | Java Core: Streams, Lambdas e Records (1/4) |
| 07:25–07:50 | **Bloco profundo** | Java Core: Streams, Lambdas e Records (2/4) |
| 07:50–08:15 | **Bloco profundo** | Java Core: Streams, Lambdas e Records (3/4) |
| 08:30–09:00 | Margem | Margem antes (EmpresaA) |
| 09:00–11:00 | Aula | EmpresaA · UNIDADE_CENTRO · Turma T01 |
| 11:00–11:30 | Deslocamento | TRANSITO · Retorno para casa |
| 11:30–12:00 | Bloco interrompível | Disponível (0h30) |
| 12:00–13:00 | Almoço | CASA · Almoço |
| 13:10–13:35 | **Bloco profundo** | Java Core: Streams, Lambdas e Records (4/4) |
| 13:35–14:00 | **Bloco profundo** | Java Collections e Complexidade (1/6) |
...

## Quarta-feira, 25/11
| Horário | Tipo | Detalhe |
| :--- | :--- | :--- |
| 07:00–07:25 | Bloco interrompível | SQL e Modelagem Relacional (1/3) |
| 07:25–07:50 | Bloco interrompível | SQL e Modelagem Relacional (2/3) |
| 08:00–09:15 | Treino | ACADEMIA · Treino matinal |
| 09:25–09:40 | Bloco interrompível | SQL e Modelagem Relacional (3/3) |
| 09:40–10:00 | Bloco interrompível | Flashcards e Revisão Leve de Conceitos Java (1/3) |
| 10:00–10:20 | Bloco interrompível | Flashcards e Revisão Leve de Conceitos Java (2/3) |
...
| 15:00–15:20 | Bloco de vigilância | Flashcards e Revisão Leve de Conceitos Java (3/3) |
| 15:20–17:00 | Bloco de vigilância | Janela de plantão compatível apenas com tarefas leves |
| 17:00–23:00 | **Bloco profundo** | Disponível (6h00) |

## Checklist de estudos
- [ ] seg 07:00–07:25 · Java Core: Streams, Lambdas e Records (1/4)
- [ ] seg 07:25–07:50 · Java Core: Streams, Lambdas e Records (2/4)
...
- [ ] qua 07:00–07:25 · SQL e Modelagem Relacional (1/3)
- [ ] qua 09:25–09:40 · SQL e Modelagem Relacional (3/3)
- [ ] qua 15:00–15:20 · Flashcards e Revisão Leve de Conceitos Java (3/3)

## Avisos
- Nenhum aviso.
```

## Princípios

- **Local primeiro:** nenhum dado pessoal ou material de empresa sai da máquina.
- **Planejamento determinístico:** a IA só gera texto.
- **Dados pessoais fora do Git:** `dados/` ignorado; só exemplos sintéticos versionados.

## Documentação de desenvolvimento

Toda a documentação técnica fica em [`dev/`](dev/README.md): requisitos, regras, arquitetura, design de saída, memória do projeto e os planos de fase em `dev/tasks/`.
