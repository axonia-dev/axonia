package dev.axonia.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentConnectionLifetimes.class)
class AgentConnectionConfig {
}
