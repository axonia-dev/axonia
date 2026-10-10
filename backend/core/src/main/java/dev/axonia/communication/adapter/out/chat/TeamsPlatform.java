package dev.axonia.communication.adapter.out.chat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.ChatPlatformName;

@Component
class TeamsPlatform implements ChatPlatform {

    private static final List<String> HOST_SUFFIXES = List.of(".environment.api.powerplatform.com", ".logic.azure.com",
            ".webhook.office.com");

    @Override
    public ChatPlatformName name() {

        return ChatPlatformName.TEAMS;
    }

    /**
     * Every route a Teams webhook has taken. Power Automate moved its trigger endpoint from {@code *.logic.azure.com}
     * to {@code *.environment.api.powerplatform.com} and retired the old host for commercial tenants on 30 November
     * 2025, so a flow built today hands the leader something like
     * {@code https://<environment>.1f.environment.api.powerplatform.com:443/powerautomate/automations/direct/...}. The
     * segment ahead of {@code environment} varies by tenant and region, which is why this matches a suffix rather than
     * a fixed host. The legacy {@code *.logic.azure.com} URLs and the older Office 365 connector
     * ({@code *.webhook.office.com}) stay claimed: a destination configured before the move must keep working.
     *
     * <p>
     * The leading dot is load-bearing - a bare {@code endsWith} would also claim
     * {@code notlogic.azure.com.attacker.test}.
     *
     * <p>
     * Sovereign clouds are deliberately absent. Microsoft excluded them from that migration and publishes no host shape
     * this could be written against, and a guessed domain in an allowlist that decides whether a request leaves the
     * network is worse than a missing one: the missing one fails closed, with the message
     * {@code ChatDestinationService} names the platform in. Add one when a tenant on that cloud reports it, not before.
     */
    @Override
    public boolean claims(URI url) {

        var host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        return HOST_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    /**
     * An Adaptive Card inside the {@code message} envelope both routes accept: a Power Automate "post a card" flow and
     * the legacy connector read the same shape, which is what lets one payload serve both without axonia knowing which
     * route the leader built.
     *
     * <p>
     * Adaptive Card {@code TextBlock} renders a restricted markdown, so member-typed text is escaped for the constructs
     * it honours - emphasis ({@code *}/{@code _}), inline code ({@code `}) and link brackets ({@code [}/{@code ]}) -
     * for the same reason Slack's is. List markers are deliberately left alone: a follow-up note someone typed as a
     * dash list should still read as one, not as a literal {@code \-}.
     */
    @Override
    public Map<String, Object> payload(ChatPost post) {

        var body = new ArrayList<Map<String, Object>>();
        if (post.sender() != null) {
            body.add(textBlock(escape(post.sender()), "Bolder", true));
        }
        body.add(textBlock(escape(post.headline()), "Default", true));
        body.add(textBlock(escape(post.whenLine()), "Default", false));
        if (post.body() != null) {
            body.add(textBlock(escape(post.body()), "Default", false));
        }
        post.details().forEach(
                (label, value) -> body.add(textBlock("**" + escape(label) + ":** " + escape(value), "Default", false)));
        var card = Map.of("type", "AdaptiveCard", "version", "1.4", "body", List.copyOf(body), "actions",
                List.of(Map.of("type", "Action.OpenUrl", "title", post.actionLabel(), "url", post.actionUrl())));
        return Map.of("type", "message", "attachments",
                List.of(Map.of("contentType", "application/vnd.microsoft.card.adaptive", "content", card)));
    }

    private static Map<String, Object> textBlock(String text, String weight, boolean wrapAsHeading) {

        return Map.of("type", "TextBlock", "text", text, "weight", weight, "wrap", true, "size",
                wrapAsHeading ? "Medium" : "Default");
    }

    /**
     * Escapes the markdown an Adaptive Card TextBlock actually honours, so member-typed text renders as typed. Order
     * matters: the backslash goes first, or a member's own backslash pairs up with one this method adds and unescapes
     * the character behind it - the same escaping-order rule Slack's own {@code escape} follows for the ampersand.
     * Typing {@code \[Click here\](https://evil.test)} in an attempt to defang their own brackets must not instead hand
     * the unescaped pair back to the renderer as a live link.
     */
    private static String escape(String raw) {

        return raw == null ? "" : raw.replace("\\", "\\\\").replaceAll("([*_`\\[\\]])", "\\\\$1");
    }
}
