package dev.axonia.identity.internal;

import java.net.URI;
import java.util.Locale;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * May avatar bytes be fetched from this URL?
 *
 * <p>
 * Fetching an identity-provider-supplied URL server-side is an SSRF vector that does not exist while the URL is only
 * ever rendered by the member's own browser. This policy is the first of the constraints that close it; the rest
 * (address-range rejection, byte ceiling, content type, timeouts) live in {@link ClaimUrlPhotoSource}, because they can
 * only be applied while the fetch is in flight.
 *
 * <p>
 * Suffix matching requires a dot boundary on purpose: without it {@code evilgoogleusercontent.com} would satisfy an
 * allowlist entry of {@code googleusercontent.com}.
 */
@Component
@RequiredArgsConstructor
class AvatarHostPolicy {

    private final AvatarProperties properties;

    boolean permits(URI uri) {

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
            return false;
        }
        var host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        var normalized = host.toLowerCase(Locale.ROOT);
        return properties.allowedHosts().stream().map(allowed -> allowed.toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed));
    }
}
