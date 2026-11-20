# Sistema de Design (Saída e Experiência) – Cadência

Como o Cadência é uma aplicação de linha de comando, o "design" define **o formato dos Markdown gerados** e **o comportamento da CLI**, para que a leitura no AppFlowy, no Notion ou no editor seja rápida e consistente.

---

## 1. Princípios
* **Legível em 30 segundos:** o professor deve entender a semana olhando um único arquivo.
* **Editável à mão:** nada de formatação que quebre ao ser editada.
* **Estável:** mesma entrada gera o mesmo arquivo, facilitando comparar semanas no Git.
* **Acessível a qualquer editor:** só Markdown padrão (tabelas, listas, checklists).

## 2. Convenções de Arquivos

| Item | Padrão |
| :--- | :--- |
| Plano semanal | `dados/semana/AAAA-Wnn.md` (ISO, ex.: `2026-W48.md`) |
| Feedback semanal | `dados/feedback/AAAA-Wnn.md` |
| Roteiros de aula | `dados/roteiros/<empresa>/<tema>.md` |
| Nomes | kebab-case, sem espaços ou acentos |
| Codificação | UTF-8, quebra de linha LF |

## 3. Estrutura do Plano Semanal

```markdown
# Semana 2026-W48 (23/11 a 29/11)

## Resumo
- Blocos livres: 14 (profundo: 4 · interrompível: 7 · vigilância: 3)
- Tempo livre total: 18h30
- Turmas em transição: nenhuma

## Segunda-feira, 23/11
| Horário | Tipo | Detalhe |
| :--- | :--- | :--- |
| 08:30–09:00 | Margem | Antes da aula EmpresaA |
| 09:00–11:00 | Aula | EmpresaA · Turma T01 |
| 20:00–22:00 | Bloco profundo | Java Collections (1/6) |

## Checklist de estudos
- [ ] seg 20:00 · Java Collections (1/6)
- [ ] ter 15:00 · Flashcards Java (1/4)

## Avisos
- Quarta: reunião EmpresaA às 11h; treino deve terminar até 09h45.
```

## 4. Rótulos de Tipo de Bloco

| Tipo | Marcador | Uso |
| :--- | :--- | :--- |
| `PROFUNDO` | **Bloco profundo** | Código e conteúdo novo |
| `INTERROMPIVEL` | Bloco interrompível | Revisão e tarefas retomáveis |
| `VIGILANCIA` | Bloco de vigilância | AE vazia e plantão: tarefas leves |

## 5. Formato do Feedback

```markdown
# Feedback 2026-W48
- [x] seg 20:00 Java Collections (1/6) | CONCLUIDO
- [ ] ter 15:00 Flashcards Java (1/4) | INTERROMPIDO | parei no card 12
- [ ] qua 19:00 Spring Boot (1/5) | NAO_ACONTECEU

## Notas livres
Semana corrida; plantão foi tranquilo.
```

Regras: `CONCLUIDO`, `INTERROMPIDO` ou `NAO_ACONTECEU`; o texto após o terceiro `|` é a nota "parei aqui".

## 6. Comportamento da CLI

| Aspecto | Padrão |
| :--- | :--- |
| Sucesso | Mensagem curta com o caminho do arquivo gerado, exit code `0` |
| Erro de dados | Mensagem com **arquivo, localização, campo e como corrigir**, exit code `2` (ver 6.1) |
| Erro inesperado | Mensagem resumida e `--verbose` para o stack trace, exit code `1` |
| IA indisponível | Aviso, o plano é gerado normalmente, exit code `0` |
| Idioma | Português do Brasil |

### 6.1 Erros de dados: sintaxe x validação

Os dois tipos de erro são tratados separadamente:

| Tipo | Origem | Localização informada |
| :--- | :--- | :--- |
| **Sintaxe YAML** (indentação, aspas, tipo incompatível) | Exceção do Jackson (`JsonProcessingException.getLocation()`) | Linha e coluna |
| **Validação de campo** (Bean Validation no DTO) | `ConstraintViolation.getPropertyPath()` | Caminho do campo, ex.: `compromissos[3].fim` (sem linha) |

Exemplos:

```text
Erro de sintaxe em dados/agenda.yaml (linha 14, coluna 9): indentação inesperada. Confira os espaços antes do campo.
Erro em dados/agenda.yaml (campo compromissos[3].fim): horário "25:00" inválido. Use HH:mm entre 00:00 e 23:59.
```

## 7. Paleta e Estilo
Não há interface gráfica no MVP. Se uma interface web for criada no futuro, a identidade visual será definida em novo ADR.
