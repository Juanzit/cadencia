package dev.cadencia;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Fronteiras arquiteturais do Cadência (dev/RULES.md §1, dev/ARCHITECTURE.md §1).
 *
 * <p>Arquitetura modular: cada módulo ({@code agenda}, {@code planejamento}, {@code feedback},
 * {@code turmas}, {@code ia}) contém sua porta {@code *Repository} (interface), seus adapters
 * ({@code Yaml*Repository}, {@code Csv*Repository}, {@code Markdown*Repository}) e seu
 * {@code *Service}. Por isso as regras identificam papéis por convenção de nome/tipo, e não por
 * pacotes globais {@code service}/{@code repository}.
 *
 * <p><b>Validação estrita:</b> todas as regras arquiteturais operam sem {@code allowEmptyShould(true)},
 * incluindo {@code cliNaoAcessaAdaptersNemArquivos} sobre os comandos Picocli em {@code dev.cadencia.cli}.
 */
@AnalyzeClasses(packages = "dev.cadencia", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    /** Qualquer repositório (porta ou adapter) — a CLI deve falar apenas com services. */
    private static final DescribedPredicate<JavaClass> QUALQUER_REPOSITORY = DescribedPredicate.describe(
            "repositórios (portas ou adapters)",
            c -> c.getSimpleName().endsWith("Repository"));

    /** Adapter concreto de persistência: classe (não interface) cujo nome termina em "Repository". */
    private static final DescribedPredicate<JavaClass> ADAPTER_REPOSITORY = DescribedPredicate.describe(
            "adapters concretos de Repository (ex.: YamlAgendaRepository)",
            c -> !c.isInterface() && c.getSimpleName().endsWith("Repository"));

    /** APIs de leitura/escrita de arquivos (java.nio.file.Path continua permitido para argumentos). */
    private static final DescribedPredicate<JavaClass> IO_DE_ARQUIVO = DescribedPredicate.describe(
            "APIs de leitura e escrita de arquivos",
            c -> c.getName().equals("java.nio.file.Files")
                    || c.getName().equals("java.nio.channels.FileChannel")
                    || (c.getPackageName().equals("java.io") && c.getSimpleName().startsWith("File")));

    // Regra A — domínio isolado de frameworks, serialização e I/O.
    @ArchTest
    static final ArchRule dominioNaoDependeDeFrameworksNemIo = noClasses()
            .that().resideInAPackage("dev.cadencia.dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.fasterxml.jackson..",
                    "java.io..",
                    "java.nio.file..")
            .because("o domínio deve ser composto de records e regras puras (dev/RULES.md §1)");

    // Regra B — CLI conversa apenas com services, nunca com repositórios, adapters ou arquivos.
    @ArchTest
    static final ArchRule cliNaoAcessaAdaptersNemArquivos = noClasses()
            .that().resideInAPackage("dev.cadencia.cli..")
            .should().dependOnClassesThat(QUALQUER_REPOSITORY.or(IO_DE_ARQUIVO))
            .because("a CLI apenas recebe argumentos, chama services e imprime resultados");

    // Regra C — services dependem de interfaces Repository, não de adapters concretos.
    @ArchTest
    static final ArchRule servicesDependemDeInterfacesRepository = noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat(ADAPTER_REPOSITORY)
            .because("services devem depender da porta *Repository, permitindo trocar o adapter");

    // Direção CLI -> SERVICE -> REPOSITORY -> ADAPTER: camadas inferiores não conhecem a CLI.
    @ArchTest
    static final ArchRule camadasInferioresNaoDependemDaCli = noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .or().haveSimpleNameEndingWith("Repository")
            .or().resideInAPackage("dev.cadencia.dominio..")
            .should().dependOnClassesThat().resideInAPackage("dev.cadencia.cli..")
            .because("a dependência flui da CLI para baixo, nunca o contrário");
}
