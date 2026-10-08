package dev.axonia.selfhosted.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Refuses to start while a setting of the application's own configuration files still names a variable nobody set. The
 * configuration binder would hand such a setting over as the literal text of its placeholder, and two settings naming
 * the same unset variable would then agree with each other: the database role's password and the password the
 * application connects with would both become that public text.
 *
 * <p>
 * An initializer, registered in {@code META-INF/spring.factories} and ordered last, so it runs once every other
 * initializer has added its settings and before the context refreshes: before any auto-configuration condition reads a
 * setting, which would otherwise fail on the first unresolved one alone, and before anything touches the database. Only
 * names declared in configuration files are checked, each by its effective value, so a file's placeholder that a
 * profile, a variable, an argument or a test overrides is not reported.
 */
class ResolvedConfigurationGuard implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    @Override
    public void initialize(ConfigurableApplicationContext context) {

        var unresolved = unresolvedSettings(context.getEnvironment());
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException(
                    "Settings that name a variable which is not set: " + String.join("; ", unresolved));
        }
    }

    @Override
    public int getOrder() {

        return Ordered.LOWEST_PRECEDENCE;
    }

    static List<String> unresolvedSettings(ConfigurableEnvironment environment) {

        return environment.getPropertySources().stream().filter(OriginTrackedMapPropertySource.class::isInstance)
                .map(EnumerablePropertySource.class::cast).flatMap(source -> Arrays.stream(source.getPropertyNames()))
                .distinct().sorted().map(name -> unresolved(environment, name)).flatMap(Optional::stream).toList();
    }

    private static Optional<String> unresolved(ConfigurableEnvironment environment, String name) {

        try {
            environment.getProperty(name);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of(name + " (" + e.getMessage() + ")");
        }
    }
}
