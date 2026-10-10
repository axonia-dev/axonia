package dev.axonia.identity.configuration;

import java.time.Clock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import dev.axonia.identity.adapter.in.web.UserProvisioningFilter;
import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.api.spi.SignInObserver;
import dev.axonia.identity.application.UserService;

/**
 * Registers {@link UserProvisioningFilter} as a plain servlet filter at lowest precedence - after Spring Security's
 * chain, so the caller is already authenticated when it runs. Registering here (rather than annotating the filter
 * {@code @Component}) keeps it out of {@code @WebMvcTest} controller slices, which would otherwise instantiate the
 * filter and fail on the absent {@code UserService}.
 */
@Configuration
public class UserProvisioningFilterConfig {

    @Bean
    FilterRegistrationBean<UserProvisioningFilter> userProvisioningFilter(UserService userService,
            SignInObserver observer, ClaimsReader claimsReader, Clock clock) {

        var registration = new FilterRegistrationBean<>(
                new UserProvisioningFilter(userService, observer, claimsReader, clock));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
