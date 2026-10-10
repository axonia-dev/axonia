package dev.axonia.organization.configuration;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import dev.axonia.organization.adapter.in.web.OrganizationLifecycleFilter;
import dev.axonia.organization.application.OrganizationService;
import dev.axonia.shared.api.tenancy.OrganizationResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers {@link OrganizationLifecycleFilter} as a plain servlet filter, after Spring Security's chains, so the
 * caller is already authenticated when it runs. Registering here rather than annotating the filter {@code @Component}
 * keeps it out of {@code @WebMvcTest} controller slices, which auto-detect {@code Filter} beans and would fail on the
 * absent {@code OrganizationService}.
 *
 * <p>
 * One step ahead of {@code UserProvisioningFilter} (lowest precedence): a suspended Organization must be refused before
 * a profile is provisioned into it.
 *
 * <p>
 * A filter that audits what a delegated session did may sit at that same lowest precedence, so running ahead of it is
 * equally deliberate: a refused write leaves no audit row. That is the wanted outcome, not an oversight. No act
 * occurred - the request never reached a controller - and recording one would mean writing into the tables of an
 * account that has just been refused as offline. Reorder these at your peril.
 */
@Configuration
class OrganizationLifecycleFilterConfig {

    @Bean
    FilterRegistrationBean<OrganizationLifecycleFilter> organizationLifecycleFilter(OrganizationResolver resolver,
            OrganizationService organizations, JsonMapper jsonMapper) {

        var registration = new FilterRegistrationBean<>(
                new OrganizationLifecycleFilter(resolver, organizations, jsonMapper));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 1);
        return registration;
    }
}
