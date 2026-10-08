package dev.axonia.roundup.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link RoundupProperties}. The module's other beans are plain {@code @Component}s. */
@Configuration
@EnableConfigurationProperties(RoundupProperties.class)
class RoundupConfig {
}
