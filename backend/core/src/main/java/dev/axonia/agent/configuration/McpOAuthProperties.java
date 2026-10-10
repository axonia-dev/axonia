package dev.axonia.agent.configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Hosts whose Client ID Metadata Documents are fetched. Empty turns the OAuth flow off, existing connections included:
 * both grants and every {@code /mcp} call refuse a client whose host is no longer listed.
 */
@ConfigurationProperties(prefix = "axonia.mcp.oauth")
public record McpOAuthProperties(@DefaultValue Set<String> trustedClientDomains) {

    public boolean trustsHostOf(String clientId) {

        try {
            var host = new URI(clientId).getHost();
            return host != null && trustedClientDomains.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
