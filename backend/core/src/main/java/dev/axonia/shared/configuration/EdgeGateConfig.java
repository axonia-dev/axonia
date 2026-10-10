package dev.axonia.shared.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import dev.axonia.shared.adapter.in.web.EdgeGateFilter;
import dev.axonia.shared.api.web.EdgeGateStatus;

/**
 * Registers {@link EdgeGateFilter} ahead of everything, including the Spring Security chain: a request that did not
 * come through our edge should not reach authentication, rate limiting, or any controller.
 */
@Configuration
@EnableConfigurationProperties(EdgeProperties.class)
class EdgeGateConfig {

    @Bean
    @ConditionalOnProperty(prefix = "axonia.edge", name = "gate-enabled", havingValue = "true")
    FilterRegistrationBean<EdgeGateFilter> edgeGateRegistration(EdgeProperties edge) {

        var registration = new FilterRegistrationBean<>(new EdgeGateFilter(edge.sharedSecrets()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Deliberately unconditional, unlike the registration above: the whole point of reporting enforcement state is that
     * it has to be reportable when the gate is off, which is the case nothing else distinguishes.
     */
    @Bean
    EdgeGateStatus edgeGateStatus(EdgeProperties edge) {

        return edge::gateEnabled;
    }
}
