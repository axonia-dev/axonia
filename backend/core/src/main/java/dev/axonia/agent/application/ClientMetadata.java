package dev.axonia.agent.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An MCP client as its Client ID Metadata Document describes it. {@code clientHost} is the host of the
 * {@code client_id} URL, the one part of the document a person can trust, so it is shown beside the self-declared name.
 */
public record ClientMetadata(String clientId, String clientName, String clientHost, List<String> redirectUris) {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

    /** RFC 8252 section 7.3: a loopback redirect matches on scheme, host and path, on any port. */
    public boolean accepts(String redirectUri) {

        var candidate = parse(redirectUri);
        if (candidate == null || !isLoopback(candidate)) {
            return false;
        }
        return redirectUris.stream().map(ClientMetadata::parse).filter(Objects::nonNull)
                .anyMatch(registered -> sameExceptPort(registered, candidate));
    }

    public static boolean isLoopback(URI uri) {

        return "http".equals(uri.getScheme()) && uri.getHost() != null
                && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null;
    }

    private static boolean sameExceptPort(URI registered, URI candidate) {

        return registered.getHost().equalsIgnoreCase(candidate.getHost())
                && Objects.equals(registered.getRawPath(), candidate.getRawPath())
                && Objects.equals(registered.getRawQuery(), candidate.getRawQuery());
    }

    private static URI parse(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var uri = new URI(value);
            return uri.getHost() == null ? null : uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
