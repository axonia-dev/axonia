package dev.axonia.shared.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link PublicUrlProperties}. */
@Configuration
@EnableConfigurationProperties(PublicUrlProperties.class)
class PublicUrlConfig {
}
