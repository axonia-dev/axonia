package dev.axonia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This build unit names no package it cannot reach. The compiler already refuses a type it cannot see, so this reads
 * what the compiler does not check: every {@code dev/axonia/<segment>} or {@code dev.axonia.<segment>} in everything
 * the build put under {@code target/classes} - the class files, where a string, an annotation value or a reflective
 * lookup can still name a package, and the resources beside them: properties, templates, migrations and
 * {@code META-INF} entries. A segment passes when it is one of {@link #allowedSegments()}.
 */
class OpenCoreBoundaryTest {

    private static final Path BUILT = Path.of("target/classes");

    private static final Pattern REFERENCE = Pattern.compile("dev[/.]axonia[/.]([A-Za-z0-9_]+)[A-Za-z0-9_/.$]*");

    private static final Pattern ROOT_CLASS_FILE = Pattern.compile("dev/axonia/([A-Za-z0-9_]+)(\\$[^/]*)?\\.class");

    private static final Pattern SOURCE_OR_CLASS = Pattern.compile("(\\$.*)?\\.(class|java)$");

    @Test
    void namesNoPackageOutsideWhatItCanReach() {

        var builtFiles = filesUnder(BUILT);
        var allowed = allowedSegments();

        assertThat(builtFiles.keySet()).as("class files under " + BUILT).anyMatch(path -> path.endsWith(".class"));
        assertThat(builtFiles.keySet()).as("resources under " + BUILT).anyMatch(path -> path.endsWith(".properties"));
        assertThat(allowed).as("top-level packages this build unit can reach").isNotEmpty();
        assertThat(foreignReferences(builtFiles, allowed))
                .as("built classes and resources naming a dev.axonia package this build unit cannot reach. The open "
                        + "edition ships without the hosted edition's modules, so nothing in it may name one")
                .isEmpty();
    }

    @Test
    void foreignReferences_aTypeOfAForeignPackageIsReported() {

        var files = Map.of("dev/axonia/events/Holder.class", classNaming("Ldev/axonia/zzz/Thing;"));

        assertThat(foreignReferences(files, Set.of("events")))
                .containsExactly("dev/axonia/events/Holder.class names dev/axonia/zzz/Thing");
    }

    @Test
    void foreignReferences_aStringNamingAForeignPackageIsReported() {

        var files = Map.of("dev/axonia/events/Holder.class", classNaming("dev.axonia.zzz.internal"));

        assertThat(foreignReferences(files, Set.of("events")))
                .containsExactly("dev/axonia/events/Holder.class names dev.axonia.zzz.internal");
    }

    @Test
    void foreignReferences_aResourceNamingAForeignPackageIsReported() {

        var files = Map.of("axonia-core.properties", "logging.level.dev.axonia.zzz=DEBUG\n".getBytes(UTF_8));

        assertThat(foreignReferences(files, Set.of("events")))
                .containsExactly("axonia-core.properties names dev.axonia.zzz");
    }

    @Test
    void foreignReferences_aTypeOfAnOwnPackageIsNotReported() {

        var files = Map.of("dev/axonia/events/Holder.class", classNaming("Ldev/axonia/events/X;"));

        assertThat(foreignReferences(files, Set.of("events"))).isEmpty();
    }

    @Test
    void foreignReferences_aClassOfTheRootPackageIsNotReported() {

        var files = Map.of("dev/axonia/events/Holder.class", classNaming("Ldev/axonia/Slugs;"),
                "dev/axonia/Slugs.class", classNaming("dev/axonia/Slugs"));

        assertThat(foreignReferences(files, Set.of("events"))).isEmpty();
    }

    /** The top-level packages and root-package classes this build unit holds. */
    protected Set<String> allowedSegments() {

        return segmentsUnder(BUILT.resolve("dev/axonia"));
    }

    /** Each entry of a root package directory, a package by its name and a class or source file by its class name. */
    static Set<String> segmentsUnder(Path rootPackage) {

        try (Stream<Path> entries = Files.list(rootPackage)) {
            return entries.map(entry -> SOURCE_OR_CLASS.matcher(entry.getFileName().toString()).replaceFirst(""))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> foreignReferences(Map<String, byte[]> filesByPath, Set<String> allowedSegments) {

        var rootClasses = filesByPath.keySet().stream().map(ROOT_CLASS_FILE::matcher).filter(Matcher::matches)
                .map(match -> match.group(1)).collect(Collectors.toSet());
        return filesByPath.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .flatMap(file -> REFERENCE.matcher(new String(file.getValue(), ISO_8859_1)).results()
                        .filter(reference -> !allowedSegments.contains(reference.group(1))
                                && !rootClasses.contains(reference.group(1)))
                        .map(MatchResult::group).distinct().map(reference -> file.getKey() + " names " + reference))
                .toList();
    }

    private static Map<String, byte[]> filesUnder(Path directory) {

        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).collect(Collectors.toMap(
                    file -> directory.relativize(file).toString().replace('\\', '/'), OpenCoreBoundaryTest::read));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(Path file) {

        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] classNaming(String... utf8Constants) {

        var classFile = new ByteArrayOutputStream();
        classFile.writeBytes(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        for (var constant : utf8Constants) {
            var bytes = constant.getBytes(UTF_8);
            classFile.write(1);
            classFile.write(bytes.length >> 8);
            classFile.write(bytes.length);
            classFile.writeBytes(bytes);
        }
        return classFile.toByteArray();
    }
}
