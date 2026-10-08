package dev.axonia.identity.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "axonia.mcp.oauth")
record AgentConnectionLifetimes(@DefaultValue("60s") Duration codeTtl, @DefaultValue("1h") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenIdleTtl, @DefaultValue("180d") Duration connectionMaxLifetime,
        @DefaultValue("30s") Duration refreshReuseGrace, @DefaultValue("30d") Duration retiredRetention) {
}
