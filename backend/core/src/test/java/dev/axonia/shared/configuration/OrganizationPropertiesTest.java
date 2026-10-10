package dev.axonia.shared.configuration;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationPropertiesTest {

    private static OrganizationProperties bind(Map<String, String> properties) {

        var binder = new Binder(new MapConfigurationPropertySource(properties));
        return binder.bindOrCreate("axonia.organization", Bindable.of(OrganizationProperties.class));
    }

    @Test
    void theDefaultFallbackIsOffUnlessConfigured() {

        assertThat(bind(Map.of()).isAllowDefaultFallback()).isFalse();
    }

    @Test
    void bindsTheDefaultFallbackFlag() {

        var actual = bind(Map.of("axonia.organization.allow-default-fallback", "true"));

        assertThat(actual.isAllowDefaultFallback()).isTrue();
    }
}
