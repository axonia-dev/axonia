package dev.axonia.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import dev.axonia.PublicUrlProperties;

/** Binds {@link PublicUrlProperties}. */
@Configuration
@EnableConfigurationProperties(PublicUrlProperties.class)
class PublicUrlConfig {
}
