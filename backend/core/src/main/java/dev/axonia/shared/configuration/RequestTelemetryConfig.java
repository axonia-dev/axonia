package dev.axonia.shared.configuration;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.shared.adapter.in.web.RequestTelemetryFilter;
import dev.axonia.shared.api.tenancy.OrganizationResolver;
import dev.axonia.shared.api.tenancy.SubjectResolver;

/**
 * Registers {@link RequestTelemetryFilter} one step behind Spring Security's chain, which is the mirror image of what
 * {@link EdgeGateConfig} does: the gate has to run before anything else, and this one cannot run until authentication
 * has. A lower order would place the filter outside the security proxy, where the SecurityContext is still empty and
 * both resolvers answer {@link java.util.Optional#empty()} on every request.
 *
 * <p>
 * Registered here, through a bean, rather than by annotating the filter {@code @Component}: a component-scanned
 * {@code Filter} is contributed to every {@code @WebMvcTest} slice in this suite, each of which would then have to
 * supply an {@link OrganizationResolver} and a {@link SubjectResolver} it has no other use for. A
 * {@code @Configuration} the slices do not import contributes nothing to them.
 */
@Configuration
class RequestTelemetryConfig {

    @Bean
    FilterRegistrationBean<RequestTelemetryFilter> requestTelemetryRegistration(OrganizationResolver organizations,
            SubjectResolver subjects) {

        var registration = new FilterRegistrationBean<>(new RequestTelemetryFilter(organizations, subjects));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
