package dev.axonia;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.BootstrapUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A static nested {@code @Configuration} or {@code @TestConfiguration} of a Spring test is used only when Spring
 * detects it, which it does for a test that declares no configuration classes, or when an {@code @Import} names it. A
 * test that names its application, as every open one does, drops an unimported nested configuration in silence, and one
 * that only overrides a bean then passes against the real bean. Each build unit checks every Spring test it compiles:
 * this class in core, {@code CloudNestedTestConfigurationIsImportedTest} in cloud.
 */
class NestedTestConfigurationIsImportedTest {

    @Test
    void everyNestedConfigurationOfASpringTestIsUsed() {

        // given
        var tests = CompiledTestClasses.springTestsBeside(getClass());

        // when
        var dropped = tests.stream().flatMap(type -> droppedConfigurationsOf(type).stream()).sorted().toList();

        // then
        assertThat(tests).as("the scan sees this build unit's Spring tests").isNotEmpty();
        assertThat(dropped).as("nested configurations Spring drops: name each in an @Import on the test").isEmpty();
    }

    private static List<String> droppedConfigurationsOf(Class<?> test) {

        var used = new HashSet<>(List.of(
                BootstrapUtils.resolveTestContextBootstrapper(test).buildMergedContextConfiguration().getClasses()));
        used.addAll(importedBy(test));
        var dropped = new ArrayList<String>();
        for (var type = test; type != null && type != Object.class; type = type.getSuperclass()) {
            for (var nested : type.getDeclaredClasses()) {
                if (Modifier.isStatic(nested.getModifiers())
                        && MergedAnnotations.from(nested).isPresent(Configuration.class) && !used.contains(nested)) {
                    dropped.add(test.getName() + " drops " + nested.getName());
                }
            }
        }
        return dropped;
    }

    private static Set<Class<?>> importedBy(Class<?> test) {

        var imported = new HashSet<Class<?>>();
        for (var type = test; type != null; type = type.getEnclosingClass()) {
            MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY).stream(Import.class)
                    .forEach(annotation -> imported.addAll(List.of(annotation.getClassArray("value"))));
        }
        return imported;
    }
}
