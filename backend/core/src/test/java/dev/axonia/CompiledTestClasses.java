package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.util.ClassUtils;

/** The compiled test classes of the build unit a class was loaded from, read from its own output directory. */
public final class CompiledTestClasses {

    private CompiledTestClasses() {
    }

    /** Every concrete Spring test class compiled beside {@code anchor}, nested ones included. */
    public static List<Class<?>> springTestsBeside(Class<?> anchor) {

        return classesBeside(anchor).stream().filter(CompiledTestClasses::isConcrete)
                .filter(type -> TestContextAnnotationUtils.hasAnnotation(type, BootstrapWith.class)).toList();
    }

    /**
     * Whether {@code type}, or a class it is nested in, inherits {@link CoreSpringBootTest} or imports
     * {@link ClosedPortStandIns}: the configuration of core's own tests, which an application's tests must never run
     * on.
     */
    public static boolean inheritsCoreConfiguration(Class<?> type) {

        for (var candidate = type; candidate != null; candidate = candidate.getEnclosingClass()) {
            var annotations = MergedAnnotations.from(candidate, SearchStrategy.TYPE_HIERARCHY);
            if (annotations.isPresent(CoreSpringBootTest.class) || annotations.stream(Import.class).anyMatch(
                    imported -> List.of(imported.getClassArray("value")).contains(ClosedPortStandIns.class))) {
                return true;
            }
        }
        return false;
    }

    /** Every class compiled beside {@code anchor}: its build unit's test classes, never those of a dependency. */
    public static List<Class<?>> classesBeside(Class<?> anchor) {

        var root = outputDirectoryOf(anchor);
        var classes = new ArrayList<Class<?>>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(file -> file.toString().endsWith(".class")).forEach(file -> {
                var relative = root.relativize(file).toString();
                var name = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                classes.add(ClassUtils.resolveClassName(name, anchor.getClassLoader()));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return classes;
    }

    private static Path outputDirectoryOf(Class<?> anchor) {

        try {
            return Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isConcrete(Class<?> type) {

        return !type.isInterface() && !type.isAnnotation() && !type.isEnum() && !type.isRecord()
                && !type.isAnonymousClass() && !type.isLocalClass() && !Modifier.isAbstract(type.getModifiers());
    }
}
