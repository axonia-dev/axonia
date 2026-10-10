package dev.axonia.identity.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import dev.axonia.identity.application.ConnectionLifetimes;

@ConfigurationProperties(prefix = "axonia.mcp.oauth")
public record AgentConnectionLifetimes(@DefaultValue("60s") Duration codeTtl,
        @DefaultValue("1h") Duration accessTokenTtl, @DefaultValue("30d") Duration refreshTokenIdleTtl,
        @DefaultValue("180d") Duration connectionMaxLifetime, @DefaultValue("30s") Duration refreshReuseGrace,
        @DefaultValue("30d") Duration retiredRetention) implements ConnectionLifetimes {
}
