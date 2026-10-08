package dev.axonia.communication.internal;

import java.net.URI;
import java.util.Map;

import dev.axonia.communication.ChatPlatformName;

/**
 * One chat product: the hosts it claims, and how a {@link ChatPost} becomes the JSON body that product's webhook
 * accepts. Splitting claim from payload is what lets {@link ChatPlatforms} answer "will axonia post here" without
 * anything having been rendered yet.
 */
interface ChatPlatform {

    ChatPlatformName name();

    /** Whether this platform owns the URL's host. The caller has already enforced HTTPS and rejected credentials. */
    boolean claims(URI url);

    Map<String, Object> payload(ChatPost post);
}
