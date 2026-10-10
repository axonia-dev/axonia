package dev.axonia.agent.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Abuse backstops for the MCP surface: per-token tool-call volume and per-address failed-authentication volume.
 *
 * <p>
 * The failure budget is deliberately looser than the call budget. A real per-address key introduces a cost the old
 * shared key never had: an enterprise behind one egress address puts its whole agent fleet in one bucket, so a single
 * misconfigured agent retrying in a loop would spend the customer's budget and leave its colleagues reading 429 where a
 * 401 would have told them what was actually wrong.
 *
 * <p>
 * The token endpoint is anonymous, so its budget is per address like failed authentication. The consent routes are
 * signed in, so theirs is per person: generous for someone clicking through a consent page, and a ceiling on how often
 * one account can make axonia fetch a client's metadata document.
 */
@ConfigurationProperties(prefix = "axonia.mcp.rate-limit")
record McpRateLimitProperties(@DefaultValue("120") int callsPerMinute, @DefaultValue("30") int authFailuresPerMinute,
        @DefaultValue("60") int tokenRequestsPerMinute, @DefaultValue("30") int consentRequestsPerMinute) {
}
