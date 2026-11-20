# Regras de Desenvolvimento e Padrões de Código

**Cadência – Diretrizes Estruturais e Boas Práticas**

---

## Observação Importante!!!
1. Antes de começar qualquer task ou alteração no código fonte, é necessário realizar a **leitura obrigatória** dos arquivos `@dev/ARCHITECTURE.md`, `@dev/DESIGN.md`, `@dev/PRD.md` e `@dev/GEMINI.md`, garantindo que a task siga os requisitos estabelecidos.
2. **Proteção do `.gitignore`:** é **estritamente proibido** remover ou enfraquecer as entradas que protegem `dados/`, `feedback-real/` e `.env`. Pode-se apenas **acrescentar** novas entradas.
3. **Nunca commitar dados pessoais:** agenda real, turmas reais, materiais de empresas (EmpresaA, EmpresaB), feedback semanal real e nomes de alunos ficam em `dados/`. O repositório só contém exemplos sintéticos em `exemplos/`.
4. Após o término das tarefas, atualizar o status no arquivo `dev/tasks/` correspondente e registrar decisões relevantes no `@dev/GEMINI.md`.
5. **Só avançar de fase após o gate de verificação** do arquivo de tarefas atual estar 100% aprovado.

---

## 1. Padrões de Arquitetura e Organização (Camadas + Repository + Service + Strategy)

1. **Separação de Camadas Estrita:**
   * **CLI (Controller):** apenas recebe argumentos, chama o `Service` e imprime o resultado. Nenhuma regra de negócio.
   * **Service Layer:** concentra as regras (margens, cálculo de blocos, alocação, confiabilidade, previsão de turmas).
   * **Repository (porta) + Adapters:** interfaces no domínio (`AgendaRepository`, `BacklogRepository`...) com implementações em arquivo (YAML/CSV/MD). Nenhuma leitura de arquivo fora dos adapters. Isso permite trocar por banco de dados no futuro sem tocar nos services.
   * **Domain:** *records* e regras puras, sem dependência de Spring, Jackson ou I/O.
   * **Strategy Pattern (obrigatório):** para pontos com múltiplas formas de execução: `AlocadorStrategy`, `ExportadorStrategy`, `GeradorTexto`.

2. **Validação com DTO:**
   * Todo arquivo de entrada (YAML, CSV, MD de feedback) é lido para um **DTO**, validado (Bean Validation) e só então convertido para o domínio. Erros de sintaxe apontam arquivo, linha e coluna; erros de validação apontam arquivo e caminho do campo (`compromissos[i].campo`), conforme `@dev/DESIGN.md`.

3. **Planejamento sem IA:**
   * Cálculo de blocos, alocação, confiabilidade e previsão de turmas **nunca** dependem de LLM. A IA só gera texto (`GeradorTexto`).

---

## 2. Regras de Manipulação de Tempo

1. **NUNCA use `Date`, `Calendar` ou `LocalDateTime` sem fuso para compromissos.** Use `java.time` (`LocalDate`, `LocalTime`, `Duration`, `ZoneId`).
2. **Fuso padrão:** `America/Sao_Paulo`.
3. **Relógio injetado:** nenhuma classe chama `LocalDate.now()` diretamente; recebe um `Clock`. Isso torna os testes determinísticos.
4. Durações em **minutos inteiros** nos arquivos de configuração.
5. Compromissos e blocos são **imutáveis** (`record`); nunca alterar uma lista recebida, sempre retornar uma nova.

---

## 3. Regras de Qualidade

1. **Java 21**, formatação consistente e sem warnings no build.
2. **Testes obrigatórios** para toda regra de negócio nova, incluindo casos de borda (sobreposição de margens, compromisso que cruza a meia-noite — modelado como início + duração e fatiado na meia-noite —, dia sem blocos, backlog vazio).
3. **Cobertura mínima:** 80% em `domain` e `service`.
4. **Commits pequenos** no padrão Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`).
5. **Definição de Pronto (DoD)** de cada tarefa:
   * Código compilando, `mvn verify` verde.
   * Testes escritos e passando.
   * Status atualizado no arquivo de tarefas.
   * Decisão relevante registrada no `@dev/GEMINI.md`.

---

## 4. Regras de Saída e Experiência

1. Toda saída é **Markdown** seguindo `@dev/DESIGN.md`.
2. Mensagens de erro devem dizer **o que houve, onde e como corrigir**.
3. Falha de IA nunca derruba o comando principal: o plano é gerado e a parte textual é marcada como indisponível.
4. A saída deve ser **idempotente**: rodar duas vezes com a mesma entrada gera o mesmo arquivo.
