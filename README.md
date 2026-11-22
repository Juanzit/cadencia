# Cadência

> Planejador semanal que roda 100% local: calcula seus blocos de tempo livre, distribui tarefas de estudo conforme a qualidade de cada bloco e usa um modelo local (Ollama) para gerar roteiros de aula e o feedback da semana.

**Status:** Fase 2B implementada (`14/14` tarefas das Fases 1–2B concluídas) — CLI `cadencia semana` funcional, regras JaCoCo independentes ativas nos 5 pacotes principais e critérios automatizados do Gate 3 (`2/5`) verificados; aguardando validação manual final (AppFlowy, agenda real em `dados/` e `Q3`/`Q5`) para aprovação do Gate 3.

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

Após compilar (`clean verify` ou `clean package`), execute o JAR passando `semana` e as opções opcionais `--data` e `--agenda`:

**Linux/macOS:**

```bash
java -jar target/cadencia-0.1.0-SNAPSHOT.jar semana --agenda exemplos/agenda.exemplo.yaml --data 2026-11-23
```

**Windows:**

```powershell
java -jar target\cadencia-0.1.0-SNAPSHOT.jar semana --agenda exemplos/agenda.exemplo.yaml --data 2026-11-23
```

Ou via Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=semana --agenda exemplos/agenda.exemplo.yaml --data 2026-11-23"
```

- **`--agenda <caminho>`** (opcional): substitui apenas o arquivo YAML de entrada. Quando omitido, lê `./dados/agenda.yaml` (ou `<cadencia.diretorio-dados>/agenda.yaml`).
- **`--data <AAAA-MM-DD>`** (opcional): seleciona a semana ISO correspondente à data informada (`AAAA-Wnn`). Quando omitido, usa a data atual no fuso `America/Sao_Paulo`.
- **`--verbose`** (opcional): exibe o stack trace completo em erros inesperados (código de saída `1`).
- **Arquivo gerado:** `<cadencia.diretorio-dados>/semana/AAAA-Wnn.md` (padrão `./dados/semana/AAAA-Wnn.md`), em UTF-8 com quebra de linha LF e sobrescrita idempotente.
- **Sobrescrever o diretório de dados via JVM** (coloque `-D` antes de `-jar`):

```powershell
java -Dcadencia.diretorio-dados=/caminho/temporario -jar target\cadencia-0.1.0-SNAPSHOT.jar semana --agenda exemplos/agenda.exemplo.yaml
```

- **Códigos de saída:** `0` (sucesso ou ajuda com `cadencia` sem subcomando), `2` (agenda inexistente, YAML inválido, falha de validação ou `--data` malformada), `1` (erro inesperado).

### Exemplo real da saída (anonimizado — `exemplos/agenda.exemplo.yaml`, semana `2026-W48`)

```markdown
# Semana 2026-W48 (23/11 a 29/11)

## Resumo
- Blocos livres: 24 (profundo: 18 · interrompível: 5 · vigilância: 1)
- Tempo livre total: 85h15
- Turmas em transição: nenhuma

## Segunda-feira, 23/11
| Horário | Tipo | Detalhe |
| :--- | :--- | :--- |
| 07:00–08:30 | **Bloco profundo** | Disponível (1h30) |
| 08:30–09:00 | Margem | Margem antes (EmpresaA) |
| 09:00–11:00 | Aula | EmpresaA · UNIDADE_CENTRO · Turma T01 |
| 11:00–11:30 | Deslocamento | TRANSITO · Retorno para casa |
| 11:30–12:00 | Bloco interrompível | Disponível (0h30) |
| 12:00–13:00 | Almoço | CASA · Almoço |
| 13:00–15:30 | **Bloco profundo** | Disponível (2h30) |
| 15:30–16:00 | Margem | Margem antes (EmpresaB) |
| 16:00–17:30 | Aula | EmpresaB · CASA · Turma R02 |
| 17:30–17:45 | Margem | Margem depois (EmpresaB) |
| 18:00–18:30 | Deslocamento | Deslocamento automático (CASA -> ACADEMIA) |
| 18:30–19:30 | Treino | ACADEMIA · Treino de força |
| 19:30–23:00 | **Bloco profundo** | Disponível (3h30) |

## Quarta-feira, 25/11
| Horário | Tipo | Detalhe |
| :--- | :--- | :--- |
| 07:00–08:00 | Bloco interrompível | Disponível (1h00) |
| 08:00–09:15 | Treino | ACADEMIA · Treino matinal |
| 09:15–10:30 | Bloco interrompível | Disponível (1h15) |
| 10:30–11:00 | Deslocamento | Deslocamento automático (ACADEMIA -> REMOTO) |
| 11:00–12:00 | Reunião | EmpresaA · REMOTO · Reunião pedagógica semanal |
| 12:00–12:30 | Deslocamento | Deslocamento automático (REMOTO -> CASA) |
| 12:30–13:30 | Almoço | CASA · Almoço |
| 13:30–15:00 | **Bloco profundo** | Disponível (1h30) |
| 15:00–17:00 | Bloco de vigilância | Janela de plantão compatível apenas com tarefas leves |
| 17:00–23:00 | **Bloco profundo** | Disponível (6h00) |

## Checklist de estudos
- [ ] seg 07:00–08:30 · Bloco profundo (1h30)
- [ ] seg 11:30–12:00 · Bloco interrompível (0h30)
...
- [ ] qua 15:00–17:00 · Janela de plantão compatível apenas com tarefas leves
...
- [ ] sex 10:00–11:30 · Reserva manual para estudo prático

## Avisos
- Nenhum aviso.
```

## Princípios

- **Local primeiro:** nenhum dado pessoal ou material de empresa sai da máquina.
- **Planejamento determinístico:** a IA só gera texto.
- **Dados pessoais fora do Git:** `dados/` ignorado; só exemplos sintéticos versionados.

## Documentação de desenvolvimento

Toda a documentação técnica fica em [`dev/`](dev/README.md): requisitos, regras, arquitetura, design de saída, memória do projeto e os planos de fase em `dev/tasks/`.
