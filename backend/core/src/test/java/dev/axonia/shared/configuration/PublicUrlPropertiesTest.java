package dev.axonia.shared.configuration;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every absolute link this application mints comes from one property, and one caller (the calendar feed) needs the host
 * out of it rather than the whole string. A value that is not a real origin fails no binding on its own, so these
 * refusals are what turn it into a refused boot instead of {@code null} appearing inside published identifiers.
 */
class PublicUrlPropertiesTest {

    private static PublicUrlProperties bind(String value) {

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var binder = new Binder(new MapConfigurationPropertySource(Map.of("axonia.public-base-url", value)));
        return binder.bindOrCreate("axonia", Bindable.of(PublicUrlProperties.class),
                new ValidationBindHandler(validator));
    }

    @Test
    void bindsARealOrigin() {

        assertThat(bind("https://axonia.example").publicBaseUrl()).isEqualTo("https://axonia.example");
    }

    @Test
    void refusesATrailingSlash() {

        assertThatThrownBy(() -> bind("https://axonia.example/")).rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("must not end with a trailing slash");
    }

    /** {@code URI.create("axonia.example").getHost()} is null, and a UID built from it would end {@code @null}. */
    @Test
    void refusesAValueWithNoScheme() {

        assertThatThrownBy(() -> bind("axonia.example")).rootCause().isInstanceOf(BindValidationException.class)
                .hasMessageContaining("must be an absolute http or https URL with a host");
    }

    @Test
    void refusesAnUnresolvedPlaceholder() {

        assertThatThrownBy(() -> bind("${AXONIA_PUBLIC_BASE_URL}")).rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("must be an absolute http or https URL with a host");
    }
}
