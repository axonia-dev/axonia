package dev.axonia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The default signed-in caller and the meter registry {@link ControllerTestSupport} imports. A top-level class rather
 * than one nested in the base: Spring Framework 7.1 reads a static nested configuration class of a test's superclass as
 * that test's default context configuration, which would replace the slice's own.
 */
@TestConfiguration(proxyBeanMethods = false)
class DefaultJwtTestConfig {

    @Bean
    MockMvcBuilderCustomizer defaultJwt() {

        return builder -> builder.defaultRequest(get("/").with(
                jwt().jwt(token -> token.subject(ControllerTestSupport.DEFAULT_TEST_USER).claim("org", "default"))));
    }

    /**
     * Unconditional on purpose. This carried a {@code @ConditionalOnMissingBean}, which Spring Boot documents as
     * meaningful only on auto-configuration classes: on an {@code @Import}ed {@code @TestConfiguration} it is evaluated
     * against whatever happens to be registered at that point, so it promised a fallback it could not deliver. No slice
     * extending this class defines its own {@link MeterRegistry}, so the guard was a no-op dressed as a safety net. A
     * slice that ever needs a different registry should override this bean by name.
     */
    @Bean
    MeterRegistry meterRegistry() {

        return new SimpleMeterRegistry();
    }
}
