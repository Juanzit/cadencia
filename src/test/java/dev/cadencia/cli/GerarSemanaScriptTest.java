package dev.cadencia.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cadencia.CadenciaApplication;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GerarSemanaScriptTest {

    private static final Path CAMINHO_SCRIPT = Path.of("scripts/gerar-semana.ps1");

    @TempDir
    Path tempDir;

    @Test
    void deveConterContratoCompletoEValidacoesDoScriptGerarSemana() throws IOException {
        assertThat(CAMINHO_SCRIPT).isRegularFile();
        String conteudo = Files.readString(CAMINHO_SCRIPT, StandardCharsets.UTF_8);

        assertThat(conteudo)
                .contains("[string]$Data", "[switch]$Sobrescrever")
                .contains("$PSScriptRoot")
                .contains("cadencia-0.1.0-SNAPSHOT.jar")
                .contains("agenda.yaml", "backlog.yaml")
                .contains("Get-Command 'java'")
                .contains("^(?:-\\s+)?\\[\\s\\]\\s+(.+)$")
                .contains("NAO_ACONTECEU", "- Nenhuma nota registrada.")
                .contains("--sobrescrever");
        assertThat(conteudo.lines().filter(linha -> !linha.contains("Arquivo obrigatorio ausente")))
                .allSatisfy(linha -> assertThat(linha).doesNotContain("mvnw"));

        int idxSemana = conteudo.indexOf("$ArgsSemana += 'semana'");
        int idxVerificaExitSemana = conteudo.indexOf("$ResultadoSemana.ExitCode -ne 0");
        int idxFeedback = conteudo.indexOf("Invoke-FeedbackModelo `");

        assertThat(idxSemana).isPositive();
        assertThat(idxVerificaExitSemana).isGreaterThan(idxSemana);
        assertThat(idxFeedback).isGreaterThan(idxVerificaExitSemana);
    }

    @Test
    void deveValidarSintaxePrerequisitosECasosDeBordaQuandoPowerShellEstiverDisponivel()
            throws IOException, InterruptedException {
        Optional<String> executavelPs = localizarExecutavelPowerShell();
        if (executavelPs.isEmpty()) {
            return;
        }
        String psCmd = executavelPs.get();

        // 1. Validação sintática oficial via Parser AST do PowerShell
        String comandoParse = "$t=$null;$e=$null;"
                + "[void][System.Management.Automation.Language.Parser]::ParseFile("
                + "'" + CAMINHO_SCRIPT.toAbsolutePath().toString().replace("'", "''") + "',"
                + "[ref]$t,[ref]$e);"
                + "if($e.Count -gt 0){$e|ForEach-Object{$_.ToString()};exit 1}";
        ExecucaoProcesso parse = executarProcesso(psCmd, Path.of("."), null, "-NoProfile", "-Command", comandoParse);
        assertThat(parse.exitCode()).as("Erro de sintaxe PowerShell: %s", parse.saidaCombinada()).isZero();

        // 2. Sandbox isolado com mock rápido de Java para validar pré-requisitos, argumentos e matriz de proteção
        Path sandbox = tempDir.resolve("sandbox-mock");
        Path dirExterno = tempDir.resolve("chamador-mock");
        Files.createDirectories(dirExterno);
        Path fakeBin = criarShimJava(tempDir.resolve("fake-bin-mock"), MockCadenciaMain.class);

        Path scriptIsolado = sandbox.resolve("scripts/gerar-semana.ps1");
        Files.createDirectories(scriptIsolado.getParent());
        Files.copy(CAMINHO_SCRIPT, scriptIsolado);

        Path logChamadas = sandbox.resolve("mock-java.log");
        Path flagFalhaSemana = sandbox.resolve("fail-semana.flag");
        String pathComMock = fakeBin.toAbsolutePath() + File.pathSeparator + System.getenv("PATH");

        // Pré-requisitos ausentes
        Files.createDirectories(sandbox.resolve("dados"));
        Files.writeString(sandbox.resolve("dados/agenda.yaml"), "mock: true\n", StandardCharsets.UTF_8);
        Files.writeString(sandbox.resolve("dados/backlog.yaml"), "mock: true\n", StandardCharsets.UTF_8);
        ExecucaoProcesso semJar = executarScript(psCmd, scriptIsolado, dirExterno, pathComMock);
        assertThat(semJar.exitCode()).isNotZero();
        assertThat(semJar.saidaCombinada()).contains("cadencia-0.1.0-SNAPSHOT.jar");

        Files.createDirectories(sandbox.resolve("target"));
        Files.writeString(sandbox.resolve("target/cadencia-0.1.0-SNAPSHOT.jar"), "stub", StandardCharsets.UTF_8);
        Files.delete(sandbox.resolve("dados/agenda.yaml"));
        ExecucaoProcesso semAgenda = executarScript(psCmd, scriptIsolado, dirExterno, pathComMock);
        assertThat(semAgenda.exitCode()).isNotZero();
        assertThat(semAgenda.saidaCombinada()).contains("agenda.yaml");

        Files.writeString(sandbox.resolve("dados/agenda.yaml"), "mock: true\n", StandardCharsets.UTF_8);
        Files.delete(sandbox.resolve("dados/backlog.yaml"));
        ExecucaoProcesso semBacklog = executarScript(psCmd, scriptIsolado, dirExterno, pathComMock);
        assertThat(semBacklog.exitCode()).isNotZero();
        assertThat(semBacklog.saidaCombinada()).contains("backlog.yaml");

        Files.writeString(sandbox.resolve("dados/backlog.yaml"), "mock: true\n", StandardCharsets.UTF_8);

        // Falha no comando semana interrompe antes de gerar feedback
        Files.writeString(flagFalhaSemana, "1", StandardCharsets.UTF_8);
        ExecucaoProcesso falhaSemana = executarScript(psCmd, scriptIsolado, dirExterno, pathComMock);
        assertThat(falhaSemana.exitCode()).isEqualTo(2);
        assertThat(Files.readAllLines(logChamadas, StandardCharsets.UTF_8))
                .singleElement()
                .asString()
                .contains("semana");
        assertThat(Files.exists(sandbox.resolve("dados/feedback"))).isFalse();
        Files.delete(flagFalhaSemana);
        Files.delete(logChamadas);

        // Omissão de -Data nunca repassa --data para o Java
        ExecucaoProcesso semData = executarScript(psCmd, scriptIsolado, dirExterno, pathComMock);
        assertThat(semData.exitCode()).as(semData.saidaCombinada()).isZero();
        List<String> chamadasSemData = Files.readAllLines(logChamadas, StandardCharsets.UTF_8);
        assertThat(chamadasSemData).hasSize(2);
        assertThat(chamadasSemData.get(0)).contains("semana").doesNotContain("--data");
        assertThat(chamadasSemData.get(1)).contains("feedback", "--modelo").doesNotContain("--data", "--sobrescrever");

        // Virada de ano (-Data 2025-12-31 -> 2026-W01) e reconhecimento de formato intocado SEM hífen inicial e com pipe final
        Path feedbackW01 = sandbox.resolve("dados/feedback/2026-W01.md");
        String modeloSemHifenComPipeFinal = """
                # Feedback 2026-W01

                ## Blocos e estudos
                [ ] seg 07:00–07:25 · Java Core (1/4) | NAO_ACONTECEU |
                [ ] ter 07:00 Java Core (2/4) | NAO_ACONTECEU |

                ## Notas livres
                - Nenhuma nota registrada.
                """;
        Files.createDirectories(feedbackW01.getParent());
        Files.writeString(feedbackW01, modeloSemHifenComPipeFinal, StandardCharsets.UTF_8);
        Files.delete(logChamadas);

        ExecucaoProcesso intocadoSemHifen = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComMock, "-Data", "2025-12-31");
        assertThat(intocadoSemHifen.exitCode()).as(intocadoSemHifen.saidaCombinada()).isZero();
        assertThat(intocadoSemHifen.saidaCombinada()).contains("intocado");
        List<String> chamadasVirada = Files.readAllLines(logChamadas, StandardCharsets.UTF_8);
        assertThat(chamadasVirada).hasSize(2);
        assertThat(chamadasVirada.get(1)).contains("feedback", "--modelo", "--sobrescrever", "--data 2025-12-31");

        // Matriz de arquivos que NÃO podem ser sobrescritos silenciosamente sem -Sobrescrever:
        // INTERROMPIDO, CONCLUIDO (sem hífen), nota "parei aqui" em NAO_ACONTECEU, nota livre sem hífen,
        // status inválido, horário inválido, cabeçalho de outra semana, seção ausente e arquivo vazio.
        List<String> conteudosProtegidos = List.of(
                """
                # Feedback 2026-W01

                ## Blocos e estudos
                [ ] seg 07:00–07:25 · Java Core (1/4) | INTERROMPIDO | parei no cap 3

                ## Notas livres
                - Nenhuma nota registrada.
                """,
                """
                # Feedback 2026-W01

                ## Blocos e estudos
                [x] seg 07:00–07:25 · Java Core (1/4) | CONCLUIDO

                ## Notas livres
                - Nenhuma nota registrada.
                """,
                """
                # Feedback 2026-W01

                ## Blocos e estudos
                - [ ] seg 07:00–07:25 · Java Core (1/4) | NAO_ACONTECEU | parei no exercicio 2

                ## Notas livres
                - Nenhuma nota registrada.
                """,
                """
                # Feedback 2026-W01

                ## Blocos e estudos
                - [ ] seg 07:00–07:25 · Java Core (1/4) | NAO_ACONTECEU

                ## Notas livres
                Semana corrida; plantao foi tranquilo.
                """,
                """
                # Feedback 2026-W01

                ## Blocos e estudos
                - [ ] seg 07:00–07:25 · Java Core (1/4) | PARCIAL

                ## Notas livres
                - Nenhuma nota registrada.
                """,
                """
                # Feedback 2026-W01

                ## Blocos e estudos
                - [ ] seg 25:00–26:00 · Java Core (1/4) | NAO_ACONTECEU

                ## Notas livres
                - Nenhuma nota registrada.
                """,
                """
                # Feedback 2026-W99

                ## Blocos e estudos
                - [ ] seg 07:00–07:25 · Java Core (1/4) | NAO_ACONTECEU

                ## Notas livres
                - Nenhuma nota registrada.
                """,
                """
                # Feedback 2026-W01
                ## Blocos e estudos
                - [ ] seg 07:00–07:25 · Java Core (1/4) | NAO_ACONTECEU
                """,
                "conteudo invalido solto\n",
                "");

        for (String conteudoProtegido : conteudosProtegidos) {
            Files.writeString(feedbackW01, conteudoProtegido, StandardCharsets.UTF_8);
            Files.deleteIfExists(logChamadas);

            ExecucaoProcesso protegido = executarScript(
                    psCmd, scriptIsolado, dirExterno, pathComMock, "-Data", "2025-12-31");
            assertThat(protegido.exitCode()).as(protegido.saidaCombinada()).isZero();
            assertThat(protegido.saidaCombinada()).contains("preservado", "-Sobrescrever");
            assertThat(Files.readString(feedbackW01, StandardCharsets.UTF_8)).isEqualTo(conteudoProtegido);
            assertThat(Files.readAllLines(logChamadas, StandardCharsets.UTF_8))
                    .singleElement()
                    .asString()
                    .contains("semana")
                    .doesNotContain("feedback");
        }
    }

    @Test
    void deveIntegrarScriptComFeedbackModeloRealNosSeisCenariosObrigatoriosQuandoPowerShellEstiverDisponivel()
            throws IOException, InterruptedException {
        Optional<String> executavelPs = localizarExecutavelPowerShell();
        if (executavelPs.isEmpty()) {
            return;
        }
        String psCmd = executavelPs.get();

        Path sandbox = tempDir.resolve("sandbox-real");
        Path dirExterno = tempDir.resolve("chamador-real");
        Files.createDirectories(dirExterno);
        Path fakeBin = criarShimJava(tempDir.resolve("fake-bin-real"), RealCadenciaShimMain.class);

        Path scriptIsolado = sandbox.resolve("scripts/gerar-semana.ps1");
        Files.createDirectories(scriptIsolado.getParent());
        Files.copy(CAMINHO_SCRIPT, scriptIsolado);
        Files.createDirectories(sandbox.resolve("target"));
        Files.writeString(sandbox.resolve("target/cadencia-0.1.0-SNAPSHOT.jar"), "real-shim", StandardCharsets.UTF_8);

        Files.createDirectories(sandbox.resolve("dados"));
        Files.writeString(sandbox.resolve("dados/agenda.yaml"), """
                jornada:
                  inicio: "07:00"
                  fim: "10:00"
                margens:
                  deslocamento_entre_locais_min: 0
                  por_empresa: {}
                classificacao:
                  profundo_minimo_min: 90
                  interrompivel_minimo_min: 25
                compromissos: []
                """, StandardCharsets.UTF_8);
        Path backlogPath = sandbox.resolve("dados/backlog.yaml");
        Files.writeString(backlogPath, """
                tarefas:
                  - id: "java-core"
                    titulo: "Java Core"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);

        Path logReal = sandbox.resolve("real-java.log");
        Path feedbackW48 = sandbox.resolve("dados/feedback/2026-W48.md");
        Path planoW48 = sandbox.resolve("dados/semana/2026-W48.md");
        String pathComRealShim = fakeBin.toAbsolutePath() + File.pathSeparator + System.getenv("PATH");

        // Cenário 1: geração inicial quando não existe feedback anterior
        ExecucaoProcesso cenario1 = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComRealShim, "-Data", "2026-11-23");
        assertThat(cenario1.exitCode()).as(cenario1.saidaCombinada()).isZero();
        assertThat(planoW48).isRegularFile();
        assertThat(feedbackW48).isRegularFile();

        String conteudoInicial = Files.readString(feedbackW48, StandardCharsets.UTF_8);
        assertThat(conteudoInicial)
                .startsWith("# Feedback 2026-W48\n")
                .contains("Java Core (1/2) | NAO_ACONTECEU")
                .contains("Java Core (2/2) | NAO_ACONTECEU")
                .contains("## Notas livres\n- Nenhuma nota registrada.\n");
        List<String> chamadas1 = Files.readAllLines(logReal, StandardCharsets.UTF_8);
        assertThat(chamadas1).hasSize(2);
        assertThat(chamadas1.get(0)).contains("semana", "--data 2026-11-23");
        assertThat(chamadas1.get(1)).contains("feedback", "--modelo", "--data 2026-11-23").doesNotContain("--sobrescrever");

        // Cenário 2: nova execução com modelo real intocado -> reconhecido como intocado e atualizado via --sobrescrever
        Files.delete(logReal);
        ExecucaoProcesso cenario2 = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComRealShim, "-Data", "2026-11-23");
        assertThat(cenario2.exitCode()).as(cenario2.saidaCombinada()).isZero();
        assertThat(cenario2.saidaCombinada()).contains("intocado");
        List<String> chamadas2 = Files.readAllLines(logReal, StandardCharsets.UTF_8);
        assertThat(chamadas2).hasSize(2);
        assertThat(chamadas2.get(1)).contains("feedback", "--modelo", "--sobrescrever", "--data 2026-11-23");
        assertThat(Files.readString(feedbackW48, StandardCharsets.UTF_8)).isEqualTo(conteudoInicial);

        // Cenário 3: alterar backlog de teste, rodar sem -Sobrescrever -> modelo intocado é atualizado com novas tarefas
        Files.writeString(backlogPath, """
                tarefas:
                  - id: "spring-security"
                    titulo: "Spring Security"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);
        Files.delete(logReal);

        ExecucaoProcesso cenario3 = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComRealShim, "-Data", "2026-11-23");
        assertThat(cenario3.exitCode()).as(cenario3.saidaCombinada()).isZero();
        assertThat(cenario3.saidaCombinada()).contains("intocado");

        String conteudoAtualizado = Files.readString(feedbackW48, StandardCharsets.UTF_8);
        assertThat(conteudoAtualizado)
                .contains("Spring Security (1/2) | NAO_ACONTECEU")
                .contains("Spring Security (2/2) | NAO_ACONTECEU")
                .doesNotContain("Java Core");

        // Cenário 4: preencher um item com [x] e CONCLUIDO -> sem -Sobrescrever preserva byte a byte
        String marcadoConcluido = conteudoAtualizado.replace(
                "- [ ] seg 07:00–07:25 · Spring Security (1/2) | NAO_ACONTECEU",
                "- [x] seg 07:00–07:25 · Spring Security (1/2) | CONCLUIDO");
        assertThat(marcadoConcluido).isNotEqualTo(conteudoAtualizado);
        Files.writeString(feedbackW48, marcadoConcluido, StandardCharsets.UTF_8);
        byte[] bytesConcluido = Files.readAllBytes(feedbackW48);
        Files.writeString(backlogPath, """
                tarefas:
                  - id: "mensageria"
                    titulo: "Mensageria RabbitMQ"
                    duracao_total_min: 50
                    tipos_aceitos: [PROFUNDO]
                    prioridade: 1
                """, StandardCharsets.UTF_8);
        Files.delete(logReal);

        ExecucaoProcesso cenario4 = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComRealShim, "-Data", "2026-11-23");
        assertThat(cenario4.exitCode()).as(cenario4.saidaCombinada()).isZero();
        assertThat(cenario4.saidaCombinada()).contains("preservado", "-Sobrescrever");
        assertThat(Files.readAllBytes(feedbackW48)).isEqualTo(bytesConcluido);
        assertThat(Files.readString(planoW48, StandardCharsets.UTF_8)).contains("Mensageria RabbitMQ (1/2)");
        assertThat(Files.readAllLines(logReal, StandardCharsets.UTF_8))
                .singleElement()
                .asString()
                .contains("semana")
                .doesNotContain("feedback");

        // Cenário 5: registrar nota livre e repetir execução sem -Sobrescrever -> preservação integral byte a byte
        String comNotaLivre = conteudoAtualizado.replace(
                "- Nenhuma nota registrada.",
                "- Mantive o ritmo pela manha e anotei duvidas de rede.");
        assertThat(comNotaLivre).isNotEqualTo(conteudoAtualizado);
        Files.writeString(feedbackW48, comNotaLivre, StandardCharsets.UTF_8);
        byte[] bytesNotaLivre = Files.readAllBytes(feedbackW48);
        Files.delete(logReal);

        ExecucaoProcesso cenario5 = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComRealShim, "-Data", "2026-11-23");
        assertThat(cenario5.exitCode()).as(cenario5.saidaCombinada()).isZero();
        assertThat(cenario5.saidaCombinada()).contains("preservado", "-Sobrescrever");
        assertThat(Files.readAllBytes(feedbackW48)).isEqualTo(bytesNotaLivre);
        assertThat(Files.readAllLines(logReal, StandardCharsets.UTF_8))
                .singleElement()
                .asString()
                .contains("semana")
                .doesNotContain("feedback");

        // Cenário 6: executar com -Sobrescrever -> substituição deliberada com aviso explícito e novo backlog refletido
        Files.delete(logReal);
        ExecucaoProcesso cenario6 = executarScript(
                psCmd, scriptIsolado, dirExterno, pathComRealShim, "-Data", "2026-11-23", "-Sobrescrever");
        assertThat(cenario6.exitCode()).as(cenario6.saidaCombinada()).isZero();
        assertThat(cenario6.saidaCombinada()).contains("-Sobrescrever", "sera substituido");
        List<String> chamadas6 = Files.readAllLines(logReal, StandardCharsets.UTF_8);
        assertThat(chamadas6).hasSize(2);
        assertThat(chamadas6.get(1)).contains("feedback", "--modelo", "--sobrescrever", "--data 2026-11-23");

        String conteudoFinal = Files.readString(feedbackW48, StandardCharsets.UTF_8);
        assertThat(conteudoFinal)
                .contains("Mensageria RabbitMQ (1/2) | NAO_ACONTECEU")
                .doesNotContain("Spring Security")
                .doesNotContain("Mantive o ritmo pela manha");
    }

    private static ExecucaoProcesso executarScript(
            String psCmd, Path scriptIsolado, Path diretorioTrabalho, String pathEnv, String... argsScript)
            throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>();
        comando.add("-NoProfile");
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            comando.add("-ExecutionPolicy");
            comando.add("Bypass");
        }
        comando.add("-File");
        comando.add(scriptIsolado.toAbsolutePath().toString());
        comando.addAll(List.of(argsScript));
        return executarProcesso(psCmd, diretorioTrabalho, pathEnv, comando.toArray(String[]::new));
    }

    private static Path criarShimJava(Path pastaBin, Class<?> mainClass) throws IOException {
        Files.createDirectories(pastaBin);
        String javaExecutavelReal = ProcessHandle.current()
                .info()
                .command()
                .orElse("java");
        String classpathNormalizado = System.getProperty("java.class.path").replace('\\', '/');
        Path argFile = pastaBin.resolve("java-args.txt");
        Files.writeString(
                argFile,
                "-cp\n\"" + classpathNormalizado + "\"\n",
                StandardCharsets.UTF_8);

        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Path cmd = pastaBin.resolve("java.cmd");
            String scriptCmd = "@echo off\r\n"
                    + "\"" + javaExecutavelReal + "\" \"@" + argFile.toAbsolutePath() + "\" "
                    + mainClass.getName() + " %*\r\n";
            Files.writeString(cmd, scriptCmd, StandardCharsets.UTF_8);
        } else {
            Path sh = pastaBin.resolve("java");
            String scriptSh = "#!/usr/bin/env sh\n"
                    + "exec '" + escaparAspasSimples(javaExecutavelReal) + "' '@"
                    + escaparAspasSimples(argFile.toAbsolutePath().toString()) + "' '"
                    + escaparAspasSimples(mainClass.getName()) + "' \"$@\"\n";
            Files.writeString(sh, scriptSh, StandardCharsets.UTF_8);
            sh.toFile().setExecutable(true);
        }
        return pastaBin;
    }

    private static String escaparAspasSimples(String valor) {
        return valor.replace("'", "'\"'\"'");
    }

    private static Optional<String> localizarExecutavelPowerShell() {
        List<String> candidatos = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? List.of("powershell.exe", "pwsh")
                : List.of("pwsh", "powershell");
        for (String candidato : candidatos) {
            try {
                ExecucaoProcesso check = executarProcesso(candidato, Path.of("."), null, "-NoProfile", "-Command", "exit 0");
                if (check.exitCode() == 0) {
                    return Optional.of(candidato);
                }
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return Optional.empty();
    }

    private static ExecucaoProcesso executarProcesso(
            String executavel, Path workingDir, String pathOverride, String... argumentos)
            throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>();
        comando.add(executavel);
        comando.addAll(List.of(argumentos));
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        if (pathOverride != null) {
            pb.environment().put("PATH", pathOverride);
        }
        Process processo = pb.start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean concluiu = processo.waitFor(45, TimeUnit.SECONDS);
        if (!concluiu) {
            processo.destroyForcibly();
            throw new IllegalStateException("Timeout executando " + executavel);
        }
        return new ExecucaoProcesso(processo.exitValue(), saida);
    }

    private record ExecucaoProcesso(int exitCode, String saidaCombinada) {
    }

    public static final class MockCadenciaMain {

        public static void main(String[] args) throws IOException {
            Path workspace = Path.of("").toAbsolutePath();
            Files.writeString(
                    workspace.resolve("mock-java.log"),
                    String.join(" ", args) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            List<String> argumentos = List.of(args);
            String dataArg = null;
            for (int i = 0; i < argumentos.size() - 1; i++) {
                if ("--data".equals(argumentos.get(i))) {
                    dataArg = argumentos.get(i + 1);
                }
            }
            String semanaIso = "2025-12-31".equals(dataArg) ? "2026-W01" : "2026-W48";

            if (argumentos.contains("semana")) {
                if (Files.exists(workspace.resolve("fail-semana.flag"))) {
                    System.err.println("Erro simulado na geracao do plano semanal.");
                    System.exit(2);
                }
                Path plano = workspace.resolve("dados/semana/" + semanaIso + ".md");
                Files.createDirectories(plano.getParent());
                Files.writeString(plano, "# Semana " + semanaIso + "\n", StandardCharsets.UTF_8);
                System.out.println("Plano semanal " + semanaIso + " gerado em " + plano);
                return;
            }

            if (argumentos.contains("feedback")) {
                Path fb = workspace.resolve("dados/feedback/" + semanaIso + ".md");
                if (Files.exists(fb) && !argumentos.contains("--sobrescrever")) {
                    System.err.println("Arquivo ja existe sem --sobrescrever.");
                    System.exit(2);
                }
                Files.createDirectories(fb.getParent());
                String modelo = "# Feedback " + semanaIso + "\n\n"
                        + "## Blocos e estudos\n"
                        + "- [ ] seg 07:00–07:25 · Java Core (1/4) | NAO_ACONTECEU\n\n"
                        + "## Notas livres\n"
                        + "- Nenhuma nota registrada.\n";
                Files.writeString(fb, modelo, StandardCharsets.UTF_8);
                System.out.println("Modelo de feedback " + semanaIso + " gerado em " + fb + " (1 item(ns)).");
            }
        }
    }

    public static final class RealCadenciaShimMain {

        public static void main(String[] args) throws IOException {
            Path workspace = Path.of("").toAbsolutePath();
            Files.writeString(
                    workspace.resolve("real-java.log"),
                    String.join(" ", args) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            List<String> cliArgs = new ArrayList<>();
            boolean aposJar = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!aposJar) {
                    if ("-jar".equals(arg)) {
                        i++;
                        aposJar = true;
                        continue;
                    }
                    if (arg.startsWith("-D")) {
                        int eq = arg.indexOf('=');
                        if (eq > 2) {
                            System.setProperty(arg.substring(2, eq), arg.substring(eq + 1));
                        }
                    }
                    continue;
                }
                cliArgs.add(arg);
            }

            CadenciaApplication.main(cliArgs.toArray(String[]::new));
        }
    }
}
