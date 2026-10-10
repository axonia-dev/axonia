package dev.axonia.communication.api;

import java.util.Optional;

/**
 * The one home of the webhook allowlist: which URLs axonia will make an outbound POST to, and which platform owns each.
 * Declared public because {@code community} calls it when a leader saves a URL, and the drain calls it again before
 * sending. Two copies of this list, one per module, would drift, and the copy that drifts is the one deciding whether a
 * request leaves the network.
 */
public interface ChatWebhooks {

    /** The platform owning this URL, or empty when axonia will not post to it at all. */
    Optional<ChatPlatformName> platformOf(String url);
}
