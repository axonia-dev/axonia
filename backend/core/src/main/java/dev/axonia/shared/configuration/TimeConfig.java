package dev.axonia.shared.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared infrastructure clock. Lives in {@code shared.configuration} (not in any bounded context) so that every module
 * - health's read model and identity's SCIM-token expiry check alike - injects the same {@link Clock} without a runtime
 * dependency on another module's beans. Tests substitute a fixed {@code Clock} per unit under test.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {

        return Clock.systemUTC();
    }
}
