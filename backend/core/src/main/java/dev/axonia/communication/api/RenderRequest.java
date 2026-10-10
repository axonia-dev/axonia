package dev.axonia.communication.api;

import java.util.Map;

import lombok.Builder;

/**
 * What a caller hands to {@link MessageRenderPort#render}: which template to render, the facts already resolved for it,
 * and enough about the intended recipient - {@code organizationId}, {@code userId}, {@code type} - to mint that
 * recipient's own unsubscribe footer link the exact same way {@code OutboxDrain} does for a real send. A caller outside
 * {@code communication} has no way to sign that link itself (the signing key and the token format are
 * {@code communication}'s own concern), so the port takes the three fields the signature needs rather than a pre-built
 * URL a caller could get subtly wrong.
 *
 * <p>
 * Five components push this past the constructor-argument-count convention this codebase already draws the line at
 * ({@link OutboundMessage} is the precedent) - a {@code @Builder} record rather than a five-argument method, so a call
 * site names each value instead of relying on positional order.
 */
@Builder
public record RenderRequest(String organizationId, String userId, MessageType type, String template,
        Map<String, Object> facts) {
}
