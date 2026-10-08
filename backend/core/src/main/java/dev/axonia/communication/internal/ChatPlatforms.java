package dev.axonia.communication.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.communication.ChatPlatformName;
import dev.axonia.communication.ChatWebhooks;

/**
 * Resolves a webhook URL to the one platform that owns it, and refuses every URL no platform claims.
 *
 * <p>
 * This is the SSRF control, not a convenience. axonia makes an outbound POST, from inside its own network, to a URL a
 * tenant supplied, so the question "which platform is this" and the question "may we send here at all" are deliberately
 * the same question with one answer. It runs when a leader saves the URL and again before every send, because a row
 * written by an older deploy is not something a newer one may trust.
 */
@Component
class ChatPlatforms implements ChatWebhooks {

    private final List<ChatPlatform> platforms;

    ChatPlatforms(List<ChatPlatform> platforms) {

        this.platforms = List.copyOf(platforms);
    }

    @Override
    public Optional<ChatPlatformName> platformOf(String url) {

        return parse(url).flatMap(this::owner).map(ChatPlatform::name);
    }

    /** The platform that will render and receive this post, or empty when the URL is not one axonia posts to. */
    Optional<ChatPlatform> platformFor(String url) {

        return parse(url).flatMap(this::owner);
    }

    private Optional<ChatPlatform> owner(URI url) {

        return platforms.stream().filter(platform -> platform.claims(url)).findFirst();
    }

    /**
     * HTTPS only, and no userinfo: a URL carrying credentials is never one of these products' own, and accepting one
     * would let a stored value send an {@code Authorization} anywhere the host check happened to allow.
     *
     * <p>
     * {@code url == null} is checked here rather than trusted to every caller: {@link ChatWebhooks#platformOf} is a
     * public port, and {@code new URI(null)} throws {@code NullPointerException} rather than the
     * {@link URISyntaxException} every caller already handles for a merely malformed one.
     */
    private static Optional<URI> parse(String url) {

        if (url == null) {
            return Optional.empty();
        }
        try {
            var parsed = new URI(url);
            var https = "https".equalsIgnoreCase(parsed.getScheme());
            return https && parsed.getHost() != null && parsed.getUserInfo() == null
                    ? Optional.of(parsed)
                    : Optional.empty();
        } catch (URISyntaxException notAUrl) {
            return Optional.empty();
        }
    }
}
