package dev.axonia.access.api.spi;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import dev.axonia.access.api.Permission;
import dev.axonia.access.application.KnownPermissions;
import lombok.experimental.UtilityClass;

/**
 * The permissions an application built from this test's classpath knows, found the way Spring finds them: every
 * production {@link PermissionContribution} under {@code dev.axonia}. On core's classpath that is the open set alone;
 * on the Cloud's it adds what the closed modules declare. No Spring context is started, so a mirror test stays a unit
 * test.
 */
@UtilityClass
public class ClasspathPermissions {

    private static final String BASE_PACKAGE = "dev.axonia";

    public Set<Permission> known() {

        return new KnownPermissions(contributions()).all();
    }

    public List<PermissionContribution> contributions() {

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(PermissionContribution.class));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(definition -> ClassUtils.resolveClassName(Objects.requireNonNull(definition.getBeanClassName()),
                        null))
                .filter(ClasspathPermissions::isProductionCode).map(ClasspathPermissions::instantiate).toList();
    }

    public PermissionContribution instantiate(Class<?> type) {

        if (!MergedAnnotations.from(type).isPresent(Component.class)) {
            throw new IllegalStateException(type.getName()
                    + " contributes permissions but is not a Spring component, so the application never learns of them");
        }
        return (PermissionContribution) BeanUtils.instantiateClass(type);
    }

    private boolean isProductionCode(Class<?> type) {

        var location = type.getProtectionDomain().getCodeSource().getLocation().toString();
        return !location.contains("test-classes") && !location.endsWith("-tests.jar");
    }
}
