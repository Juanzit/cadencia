# Cadência

> Planejador semanal que roda 100% local: calcula seus blocos de tempo livre, distribui tarefas de estudo conforme a qualidade de cada bloco e usa um modelo local (Ollama) para gerar roteiros de aula e o feedback da semana.

**Status:** Marco 1 (Fases 1–2B, Gate 3), Marco 2 — v0.2 (Fase 3, Gate 4) e **Marco 3 — v0.3** (Fase 4: `8/8` tarefas concluídas e **Gate 5 — O Sistema Aprende aprovado**) concluídos e publicados; próxima etapa: Fase 5 — Turmas e Transições de Módulo (`dev/tasks/_4_TASKS_TURMAS.md`).

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
  - Na tabela de cada dia e na seção `## Checklist de estudos`, cada pedaço alocado aparece no formato **`Título (k/N)`** (por exemplo, `Java Collections e Complexidade (1/6)`), indicando o `k`-ésimo pedaço de um total de `N`, sempre respeitando os `tipos_aceitos` da tarefa, a folga mínima configurável (`10` min via `cadencia.folga-minima-min`) junto a compromissos adjacentes e o **balanceamento por menor carga diária** (entre os blocos compatíveis disponíveis, prioriza o dia com menor duração já alocada na semana, desempatando cronologicamente).
  - Pedaços que não couberem por falta de tempo ou bloco compatível são listados em `## Avisos`.
- **Códigos de saída:** `0` (sucesso ou ajuda com `cadencia` sem subcomando), `2` (agenda/backlog ausente quando obrigatório, YAML inválido, falha de validação, feedback malformado ou `--data` malformada), `1` (erro inesperado).

## Feedback Semanal e Confiabilidade (`cadencia feedback`)

Para fechar o ciclo semanal de melhoria contínua sem depender de IA:

1. **Gerar o arquivo modelo da semana:**

```powershell
java -jar target\cadencia-0.1.0-SNAPSHOT.jar feedback --modelo --agenda exemplos/agenda.exemplo.yaml --backlog exemplos/backlog.exemplo.yaml --data 2026-11-23
```

   O comando cria `<cadencia.diretorio-dados>/feedback/AAAA-Wnn.md` (ex.: `dados/feedback/2026-W48.md`) preenchido com os estudos alocados (ou blocos livres, na ausência de backlog), todos inicialmente com `| NAO_ACONTECEU`. Por segurança, arquivos já existentes não são sobrescritos, salvo quando `--sobrescrever` for informado. Veja um arquivo completo em [exemplos/feedback.exemplo.md](exemplos/feedback.exemplo.md).

2. **Registrar a execução real manualmente:**

   Abra `dados/feedback/AAAA-Wnn.md` no AppFlowy ou editor de texto e marque cada item com um dos três estados válidos:
   - `[x] ... | CONCLUIDO` (`peso = 1.0`): remove qualquer nota `"parei aqui"` pendente daquela tarefa.
   - `[ ] ... | INTERROMPIDO | parei no exercício 7` (`peso = 0.5`): o texto após o terceiro `|` é guardado como nota `"parei aqui"`.
   - `[ ] ... | NAO_ACONTECEU` (`peso = 0.0`): preserva eventual nota anterior ainda em aberto.

3. **Como o feedback influencia a semana seguinte:**
   - **Nota de retomada:** na próxima execução de `cadencia semana`, a última nota `"parei aqui"` vigente reaparece automaticamente no primeiro pedaço cronológico daquela tarefa em `## Checklist de estudos` (`— retomar: ...`).
   - **Aprendizado estatístico por faixa (`ConfiabilidadeService` + `ConfiabilidadeAlocadorStrategy`):** agrupa os feedbacks por `(dia da semana, período)` (`MANHA < 12:00`, `TARDE 12:00–17:59`, `NOITE >= 18:00`) na janela móvel padrão das últimas `4` semanas (`cadencia.confiabilidade.janela-semanas`). Uma faixa só é considerada de baixa confiabilidade ao atingir `3` amostras (`cadencia.confiabilidade.minimo-amostras`) e ficar abaixo de `0.50` (`cadencia.confiabilidade.limite-confiabilidade`). Blocos `PROFUNDO` nessas faixas passam a aceitar apenas tarefas `INTERROMPIVEL` (registrando o alerta em `## Avisos`), enquanto faixas com poucas amostras preservam integralmente o comportamento padrão.

### Geração semanal em um comando (`scripts/gerar-semana.ps1`)

Para automatizar a geração do plano semanal e do modelo de feedback em um único comando no Windows (PowerShell 5.1+, funcionando a partir da raiz do projeto ou de qualquer outro diretório sobre `dados/agenda.yaml` e `dados/backlog.yaml`):

- **Uso normal** (semana atual no fuso `America/Sao_Paulo`, sem passar `--data`):

```powershell
.\scripts\gerar-semana.ps1
```

- **Uso com data específica** (`AAAA-MM-DD`, incluindo viradas de ano ISO):

```powershell
.\scripts\gerar-semana.ps1 -Data 2026-11-23
```

- **Uso com autorização explícita de sobrescrita** do feedback existente:

```powershell
.\scripts\gerar-semana.ps1 -Sobrescrever
```

**Comportamento e proteções:**

- Valida `java` no `PATH`, `target/cadencia-0.1.0-SNAPSHOT.jar`, `dados/agenda.yaml` e `dados/backlog.yaml` antes de executar (sem rodar Maven automaticamente).
- Executa `semana` primeiro e interrompe com o mesmo código de saída em caso de erro, sem gerar feedback.
- Se `dados/feedback/AAAA-Wnn.md` ainda não existir, gera o modelo com `feedback --modelo`.
- Se o feedback existir e estiver **intocado** (apenas itens `[ ]` com `NAO_ACONTECEU`, sem notas `"parei aqui"` e sem notas livres além de `- Nenhuma nota registrada.`), atualiza o modelo com `--sobrescrever`.
- Se o feedback já contiver preenchimento real (`[x]`, `CONCLUIDO`, `INTERROMPIDO`, nota `"parei aqui"` ou notas livres registradas), atualiza apenas o plano semanal e **preserva o arquivo de feedback integralmente**, salvo quando `-Sobrescrever` for informado explicitamente.

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
| 07:25–07:50 | **Bloco profundo** | Java Collections e Complexidade (4/6) |
| 07:50–08:15 | **Bloco profundo** | Spring Boot: Web, Validação e Arquitetura (6/6) |
| 08:30–09:00 | Margem | Margem antes (EmpresaA) |
| 09:00–11:00 | Aula | EmpresaA · UNIDADE_CENTRO · Turma T01 |
| 11:00–11:30 | Deslocamento | TRANSITO · Retorno para casa |
| 11:30–12:00 | Bloco interrompível | Disponível (0h30) |
| 12:00–13:00 | Almoço | CASA · Almoço |
| 13:10–13:35 | **Bloco profundo** | Testes com JUnit 5, AssertJ e Mockito (3/4) |
| 13:35–15:20 | **Bloco profundo** | Disponível (1h45) |
...

## Quarta-feira, 25/11
| Horário | Tipo | Detalhe |
| :--- | :--- | :--- |
| 07:00–07:25 | Bloco interrompível | Testes com JUnit 5, AssertJ e Mockito (2/4) |
| 07:25–07:50 | Bloco interrompível | SQL e Modelagem Relacional (1/3) |
| 08:00–09:15 | Treino | ACADEMIA · Treino matinal |
| 09:25–09:50 | Bloco interrompível | SQL e Modelagem Relacional (2/3) |
| 09:50–10:10 | Bloco interrompível | Flashcards e Revisão Leve de Conceitos Java (1/3) |
...
| 15:00–15:20 | Bloco de vigilância | Flashcards e Revisão Leve de Conceitos Java (2/3) |
| 15:20–15:40 | Bloco de vigilância | Flashcards e Revisão Leve de Conceitos Java (3/3) |
| 15:40–17:00 | Bloco de vigilância | Janela de plantão compatível apenas com tarefas leves |
| 17:00–23:00 | **Bloco profundo** | Disponível (6h00) |

## Checklist de estudos
- [ ] seg 07:00–07:25 · Java Core: Streams, Lambdas e Records (1/4)
- [ ] seg 07:25–07:50 · Java Collections e Complexidade (4/6)
...
- [ ] ter 07:00–07:25 · Java Core: Streams, Lambdas e Records (2/4)
- [ ] qua 07:00–07:25 · Testes com JUnit 5, AssertJ e Mockito (2/4)
- [ ] qui 07:00–07:25 · Java Core: Streams, Lambdas e Records (4/4)
- [ ] sex 07:00–07:25 · Java Collections e Complexidade (1/6)
- [ ] sáb 07:00–07:25 · Java Collections e Complexidade (2/6)
- [ ] dom 07:00–07:25 · Java Collections e Complexidade (3/6)

## Avisos
- Nenhum aviso.
```

## Princípios

- **Local primeiro:** nenhum dado pessoal ou material de empresa sai da máquina.
- **Planejamento determinístico:** a IA só gera texto.
- **Dados pessoais fora do Git:** `dados/` ignorado; só exemplos sintéticos versionados.

## Documentação de desenvolvimento

Toda a documentação técnica fica em [`dev/`](dev/README.md): requisitos, regras, arquitetura, design de saída, memória do projeto e os planos de fase em `dev/tasks/`.
