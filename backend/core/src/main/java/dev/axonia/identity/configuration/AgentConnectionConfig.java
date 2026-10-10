package dev.axonia.identity.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentConnectionLifetimes.class)
class AgentConnectionConfig {
}
