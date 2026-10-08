package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Every {@link IsolationProbe} on the classpath, read from the compiled test classes under {@code dev.axonia}. */
public final class IsolationProbes {

    private static final String CLASSES = "classpath*:dev/axonia/**/*.class";

    private static final Pattern SUREFIRE_DEFAULT_INCLUDES = Pattern.compile("Test.*|.*Test|.*Tests|.*TestCase");

    private IsolationProbes() {
    }

    /** Each probed table, with the {@code Class#method} probes that prove it. */
    public static Map<String, SortedSet<String>> byTable() {

        var probes = new TreeMap<String, SortedSet<String>>();
        for (var method : probeMethods()) {
            for (var probe : method.getAnnotationsByType(IsolationProbe.class)) {
                probes.computeIfAbsent(probe.value(), table -> new TreeSet<>())
                        .add(method.getDeclaringClass().getSimpleName() + "#" + method.getName());
            }
        }
        return probes;
    }

    /** Every method carrying at least one {@link IsolationProbe}. */
    public static List<Method> probeMethods() {

        var methods = new ArrayList<Method>();
        try {
            var readers = new CachingMetadataReaderFactory();
            for (var resource : new PathMatchingResourcePatternResolver().getResources(CLASSES)) {
                var metadata = readers.getMetadataReader(resource).getAnnotationMetadata();
                if (metadata.hasAnnotatedMethods(IsolationProbe.class.getName())
                        || metadata.hasAnnotatedMethods(IsolationProbe.List.class.getName())) {
                    var type = ClassUtils.resolveClassName(metadata.getClassName(),
                            IsolationProbes.class.getClassLoader());
                    for (var method : type.getDeclaredMethods()) {
                        if (method.getAnnotationsByType(IsolationProbe.class).length > 0) {
                            methods.add(method);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return methods;
    }

    /**
     * Asserts that JUnit, launched by Surefire's defaults, would discover every probe compiled beside {@code anchor}
     * and not skip it as {@code @Disabled}: a probe that never executes would keep its table on the probed side. Each
     * build unit calls this for its own probes, since Surefire runs no test class of a dependency.
     */
    public static void assertEveryProbeBesideRuns(Class<?> anchor) {

        var beside = new HashSet<>(CompiledTestClasses.classesBeside(anchor));
        var probes = probeMethods().stream().filter(method -> beside.contains(method.getDeclaringClass())).toList();

        var idle = probes.stream()
                .flatMap(method -> whyNeverRun(method, beside)
                        .map(reason -> method.getDeclaringClass().getName() + "#" + method.getName() + ": " + reason)
                        .stream())
                .sorted().toList();

        assertThat(probes).as("the scan sees the probes beside %s", anchor.getSimpleName()).isNotEmpty();
        assertThat(idle).as("probes JUnit never runs prove nothing").isEmpty();
    }

    private static Optional<String> whyNeverRun(Method probe, Set<Class<?>> beside) {

        var modifiers = probe.getModifiers();
        if (!AnnotationSupport.isAnnotated(probe, Testable.class) || Modifier.isPrivate(modifiers)
                || Modifier.isStatic(modifiers) || probe.getReturnType() != void.class) {
            return Optional.of("not a test method JUnit discovers");
        }
        if (AnnotationSupport.isAnnotated(probe, Disabled.class)) {
            return Optional.of("@Disabled");
        }
        return whyNeverRun(probe.getDeclaringClass(), beside);
    }

    /** Why neither {@code type} nor any concrete class beside it that extends it is a class JUnit runs. */
    private static Optional<String> whyNeverRun(Class<?> type, Set<Class<?>> beside) {

        var runners = beside.stream().filter(type::isAssignableFrom)
                .filter(runner -> !runner.isInterface() && !Modifier.isAbstract(runner.getModifiers())).toList();
        if (runners.isEmpty()) {
            return Optional.of(type.getName() + " is abstract, and no concrete class beside it extends it");
        }
        var reasons = runners.stream().map(runner -> whyRunnerNeverRuns(runner, beside)).toList();
        return reasons.stream().allMatch(Optional::isPresent)
                ? Optional.of(reasons.stream().map(Optional::get).sorted().collect(Collectors.joining("; ")))
                : Optional.empty();
    }

    private static Optional<String> whyRunnerNeverRuns(Class<?> runner, Set<Class<?>> beside) {

        if (AnnotationSupport.isAnnotated(runner, Disabled.class)) {
            return Optional.of("@Disabled on " + runner.getName());
        }
        var enclosing = runner.getEnclosingClass();
        if (enclosing == null) {
            return SUREFIRE_DEFAULT_INCLUDES.matcher(runner.getSimpleName()).matches()
                    ? Optional.empty()
                    : Optional.of(runner.getName() + " is outside Surefire's default includes");
        }
        if (Modifier.isStatic(runner.getModifiers()) || !AnnotationSupport.isAnnotated(runner, Nested.class)) {
            return Optional.of(runner.getName() + " is nested but not an inner @Nested class");
        }
        return whyNeverRun(enclosing, beside);
    }
}
