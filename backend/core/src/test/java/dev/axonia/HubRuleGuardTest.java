package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import dev.axonia.organization.api.OrganizationKind;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * No open module decides a Hub rule. Wherever an open module's behaviour differs for a Community Hub, it asks one of
 * its own policy ports and the hub module answers it, so the open edition compiles, runs and reads without the hub
 * module and a Hub's rules all live in one place.
 *
 * <p>
 * Two halves, because each is blind where the other sees. The bytecode rule catches every read of
 * {@code OrganizationKind.HUB} however the source spells it: qualified, statically imported, or as a classic enum
 * {@code switch} label, which javac compiles into a synthetic {@code $SwitchMap} class of the same package that reads
 * the constant. The text scan catches what never reaches that field: the string {@code "HUB"}, as in
 * {@code OrganizationKind.valueOf("HUB")} or a comparison against the kind's name, and the Hub's own types. Comment
 * lines are skipped, since a comment branches on nothing. A comparison against {@code ENTERPRISE} that means "not a
 * hub" passes both. It reads core, which holds the open modules only; {@link OpenCoreBoundaryTest} holds the dependency
 * direction, and this holds the vocabulary.
 */
class HubRuleGuardTest {

    private static final String ROOT = "dev.axonia";

    private static final Path MAIN = Path.of("src/main/java");

    private static final Path ROOT_PACKAGE = MAIN.resolve("dev/axonia");

    private static final List<String> HUB_VOCABULARY = List.of("OrganizationKind.HUB", "HubRegistration",
            "HubVisibility", "\"HUB\"");

    private static final List<String> COMMENT_OPENERS = List.of("*", "//", "/*");

    @Test
    void noOpenModuleReadsTheHubKind() {

        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        noClasses().that().doNotHaveFullyQualifiedName(OrganizationKind.class.getName()).should()
                .accessField(OrganizationKind.class, OrganizationKind.HUB.name())
                .because("an open module asks a policy port of its own, and the hub module answers it").check(classes);
    }

    @Test
    void noOpenModuleNamesTheHubKind() {

        assertThat(hits()).as("lines outside the hub module that branch on, or carry, a Hub's own vocabulary. Ask a "
                + "policy port of the open module instead, and answer it from the hub module").isEmpty();
    }

    private static List<String> hits() {

        try (Stream<Path> files = Files.walk(ROOT_PACKAGE)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().flatMap(HubRuleGuardTest::hitsIn)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> hitsIn(Path file) {

        try {
            var lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size()).filter(index -> !isComment(lines.get(index)))
                    .filter(index -> HUB_VOCABULARY.stream().anyMatch(lines.get(index)::contains))
                    .mapToObj(index -> MAIN.relativize(file) + ":" + (index + 1) + ": " + lines.get(index).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isComment(String line) {

        var stripped = line.strip();
        return COMMENT_OPENERS.stream().anyMatch(stripped::startsWith);
    }
}
