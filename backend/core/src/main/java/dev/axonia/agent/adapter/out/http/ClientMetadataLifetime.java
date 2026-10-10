package dev.axonia.agent.adapter.out.http;

import java.time.Duration;
import java.util.List;

import dev.axonia.agent.adapter.out.http.CacheDirectives.Directive;

/**
 * How long a resolved Client ID Metadata Document is kept: the {@code max-age} its response asked for, held between one
 * minute and a day, the bounds draft-ietf-oauth-client-id-metadata-document-02 section 5.2 lets the server set.
 * {@code no-store}, an unqualified {@code no-cache} and anything RFC 9111 section 4.2.1 calls stale fall to the minute,
 * which keeps one consent flow to one fetch. A response with no {@code max-age} is kept an hour. The directives of a
 * shared cache ({@code private}, {@code s-maxage}) do not apply: axonia fetches for itself.
 */
final class ClientMetadataLifetime {

    static final Duration MINIMUM = Duration.ofMinutes(1);
    static final Duration WITHOUT_MAX_AGE = Duration.ofHours(1);
    static final Duration MAXIMUM = Duration.ofHours(24);

    private ClientMetadataLifetime() {
    }

    /** {@code cacheControl} is every {@code Cache-Control} field line joined, or null when there is none. */
    static Duration of(String cacheControl) {

        if (cacheControl == null) {
            return WITHOUT_MAX_AGE;
        }
        return CacheDirectives.parse(cacheControl).map(ClientMetadataLifetime::fromDirectives).orElse(MINIMUM);
    }

    /**
     * RFC 9111 section 4.2.1: the most restrictive directive wins, and a repeated or unreadable {@code max-age} makes
     * the response stale. A qualified {@code no-cache} names header fields not to reuse (section 5.2.2.4); no header is
     * kept here, so it leaves the document cacheable.
     */
    private static Duration fromDirectives(List<Directive> directives) {

        var maxAges = directives.stream().filter(directive -> directive.is("max-age")).toList();
        if (maxAges.size() > 1 || directives.stream().anyMatch(ClientMetadataLifetime::forbidsReuse)) {
            return MINIMUM;
        }
        if (maxAges.isEmpty()) {
            return WITHOUT_MAX_AGE;
        }
        var seconds = maxAges.getFirst().deltaSeconds();
        return seconds.isPresent()
                ? Duration.ofSeconds(Math.clamp(seconds.getAsLong(), MINIMUM.toSeconds(), MAXIMUM.toSeconds()))
                : MINIMUM;
    }

    private static boolean forbidsReuse(Directive directive) {

        return directive.is("no-store") || directive.is("no-cache") && directive.argument().isEmpty();
    }
}
