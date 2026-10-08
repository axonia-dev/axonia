package dev.axonia.config;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import dev.axonia.CurrentToken;

@Configuration
@ConfigurationProperties(prefix = "axonia.organization")
public class OrganizationProperties {

    private boolean allowDefaultFallback = false;

    public void setAllowDefaultFallback(boolean v) {
        this.allowDefaultFallback = v;
    }

    public boolean isAllowDefaultFallback() {
        return allowDefaultFallback;
    }

    @PostConstruct
    void apply() {
        CurrentToken.setAllowDefaultFallback(allowDefaultFallback);
    }
}
