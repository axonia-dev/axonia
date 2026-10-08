package dev.axonia.identity.internal;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where avatar bytes may be fetched from, and how much of them we will read.
 *
 * <p>
 * The host list is deliberately static and small: it names the identity providers axonia itself federates with, not
 * anything a customer configures. A member arriving through a generic OIDC enterprise connection therefore has no
 * mirrorable avatar and falls back to initials - the accepted cost of not putting a live identity provider dependency,
 * or a second per-connection host concept, into avatar syncing.
 */
@ConfigurationProperties(prefix = "axonia.avatar")
public record AvatarProperties(List<String> allowedHosts, int maxBytes, Duration timeout) {
}
