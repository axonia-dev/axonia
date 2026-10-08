package dev.axonia.community;

import java.net.URI;

import dev.axonia.ValidationException;
import dev.axonia.communication.ChatPlatformName;
import lombok.experimental.UtilityClass;

/**
 * The URL-side rules {@link ChatDestinationService} applies: masking a URL for a leader-facing response, treating a URL
 * that no longer parses defensively rather than as a bug - an older, laxer deploy may have written one today's stricter
 * {@code java.net.URI} no longer accepts - and holding a saved URL to the platform the leader said it belonged to.
 */
@UtilityClass
class ChatWebhookUrls {

    /**
     * The declared platform narrows the allowlist rather than replacing it: {@code actual} is still the host's own
     * verdict, so a URL no platform claims is refused exactly as it was before anyone declared anything. What declaring
     * buys is the refusal itself. "That is not a Slack URL" can name the shape to paste, where a message covering both
     * products at once left someone holding a valid Teams URL from a host axonia had not yet heard of with nothing to
     * act on.
     */
    String requireDeclaredPlatform(ChatPlatformName declared, ChatPlatformName actual, String url) {

        if (declared == null) {
            throw new ValidationException("CHAT_PLATFORM_REQUIRED", "Choose which chat tool this webhook belongs to.");
        }
        if (actual != declared) {
            throw new ValidationException("INVALID_CHAT_WEBHOOK_URL", switch (declared) {
                case SLACK -> "That is not a Slack incoming webhook URL. Slack's own starts with "
                        + "https://hooks.slack.com/services/.";
                case TEAMS -> "That is not a Microsoft Teams webhook URL. In Power Automate, open your flow's Teams "
                        + "webhook trigger and copy the HTTP POST URL it shows.";
            });
        }
        return url;
    }

    /**
     * Host plus the last four characters: enough to recognise which webhook this is, useless to anyone who steals it.
     * The URL comes from a stored column, not a request that just validated it, so the host is read defensively rather
     * than trusted to parse.
     */
    String mask(String url) {

        var host = safeHost(url);
        var tail = url.length() <= 4 ? url : url.substring(url.length() - 4);
        return (host == null ? "" : host) + "/..." + tail;
    }

    String safeHost(String url) {

        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException notAUrl) {
            return null;
        }
    }

    String normalized(String raw) {

        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
