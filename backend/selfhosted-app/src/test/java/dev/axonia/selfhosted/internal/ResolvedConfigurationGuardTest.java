package dev.axonia.selfhosted.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedConfigurationGuardTest {

    private final StandardEnvironment environment = new StandardEnvironment();

    @Test
    void namesEverySettingOfAConfigurationFileThatStillNamesAnUnsetVariable() {

        // given
        fileSays(Map.of("spring.datasource.password", "${AXONIA_GUARD_TEST_UNSET_ONE}", "axonia.edge.shared-secrets",
                "${AXONIA_GUARD_TEST_UNSET_TWO}", "axonia.rate-limit.store", "memory"));

        // when
        var unresolved = ResolvedConfigurationGuard.unresolvedSettings(environment);

        // then
        assertThat(unresolved).hasSize(2);
        assertThat(unresolved.get(0)).startsWith("axonia.edge.shared-secrets (")
                .contains("AXONIA_GUARD_TEST_UNSET_TWO");
        assertThat(unresolved.get(1)).startsWith("spring.datasource.password (")
                .contains("AXONIA_GUARD_TEST_UNSET_ONE");
    }

    @Test
    void acceptsAPlaceholderWithADefaultAndOneThatAnotherSettingResolves() {

        // given
        fileSays(Map.of("first", "${AXONIA_GUARD_TEST_UNSET:fallback}", "second", "${first}"));

        // then
        assertThat(ResolvedConfigurationGuard.unresolvedSettings(environment)).isEmpty();
    }

    @Test
    void acceptsAFilesPlaceholderThatAHigherSourceOverrides() {

        // given
        fileSays(Map.of("spring.datasource.password", "${AXONIA_GUARD_TEST_UNSET}"));
        environment.getPropertySources()
                .addFirst(new MapPropertySource("variables", Map.of("spring.datasource.password", "secret")));

        // then
        assertThat(ResolvedConfigurationGuard.unresolvedSettings(environment)).isEmpty();
    }

    @Test
    void namesAFilesSettingThatAHigherSourceOverridesWithAnUnsetVariable() {

        // given
        fileSays(Map.of("axonia.public-base-url", "https://axonia.example.com"));
        environment.getPropertySources().addFirst(
                new MapPropertySource("arguments", Map.of("axonia.public-base-url", "${AXONIA_GUARD_TEST_UNSET}")));

        // then
        assertThat(ResolvedConfigurationGuard.unresolvedSettings(environment)).singleElement().asString()
                .startsWith("axonia.public-base-url (");
    }

    @Test
    void ignoresAnUnresolvedValueNoConfigurationFileNames() {

        // given
        environment.getPropertySources()
                .addFirst(new MapPropertySource("variables", Map.of("SOME_VARIABLE", "${AXONIA_GUARD_TEST_UNSET}")));

        // then
        assertThat(ResolvedConfigurationGuard.unresolvedSettings(environment)).isEmpty();
    }

    @Test
    void refusesTheStartAndNamesEachSetting() {

        // given
        fileSays(Map.of("spring.flyway.password", "${AXONIA_GUARD_TEST_UNSET}"));
        var context = new GenericApplicationContext();
        context.setEnvironment(environment);

        // then
        assertThatThrownBy(() -> new ResolvedConfigurationGuard().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Settings that name a variable which is not set: spring.flyway.password (")
                .hasMessageContaining("AXONIA_GUARD_TEST_UNSET");
    }

    @Test
    void letsAResolvedConfigurationStart() {

        // given
        fileSays(Map.of("axonia.rate-limit.store", "memory"));
        var context = new GenericApplicationContext();
        context.setEnvironment(environment);

        // then
        assertThatCode(() -> new ResolvedConfigurationGuard().initialize(context)).doesNotThrowAnyException();
    }

    @Test
    void runsAfterEveryOtherInitializer() {

        assertThat(new ResolvedConfigurationGuard().getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    private void fileSays(Map<String, Object> settings) {

        environment.getPropertySources().addLast(new OriginTrackedMapPropertySource(
                "Config resource 'class path resource [application.properties]'", settings));
    }
}
