package dev.axonia.communication.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.communication.ChatPlatformName;

@Component
class SlackPlatform implements ChatPlatform {

    private static final String HOST = "hooks.slack.com";

    @Override
    public ChatPlatformName name() {

        return ChatPlatformName.SLACK;
    }

    @Override
    public boolean claims(URI url) {

        return HOST.equalsIgnoreCase(url.getHost());
    }

    /**
     * Block Kit, with a {@code text} fallback beside it: Slack uses that string for the notification preview and for
     * any surface that cannot render blocks, so omitting it makes a post arrive as an empty notification.
     *
     * <p>
     * Every member-typed value passes through {@link #escape}. Slack mrkdwn gives {@code <}, {@code >} and {@code &}
     * meaning - {@code <url|label>} is a link - so a session title someone typed as a link must arrive as the text they
     * typed, not as the link they were trying to plant.
     */
    @Override
    public Map<String, Object> payload(ChatPost post) {

        var blocks = new ArrayList<Map<String, Object>>();
        blocks.add(section(headerMarkdown(post)));
        blocks.add(context(escape(post.whenLine())));
        if (post.body() != null) {
            blocks.add(section(escape(post.body())));
        }
        post.details().forEach((label, value) -> blocks.add(section("*" + escape(label) + ":* " + escape(value))));
        blocks.add(Map.of("type", "actions", "elements", List.of(Map.of("type", "button", "style", "primary", "url",
                post.actionUrl(), "text", Map.of("type", "plain_text", "text", post.actionLabel())))));
        return Map.of("text", fallbackText(post), "blocks", List.copyOf(blocks));
    }

    /**
     * {@code sender} is {@code null}, never a stand-in name, when {@code ChatPostBuilders} found no community - see its
     * own javadoc. Bolding an empty name would leave a bare {@code **} ahead of the headline, so this omits the name
     * entirely rather than render broken copy, the same as mail's shell omits its identity block outright.
     */
    private static String headerMarkdown(ChatPost post) {

        return post.sender() == null
                ? escape(post.headline())
                : "*" + escape(post.sender()) + "*\n" + escape(post.headline());
    }

    private static String fallbackText(ChatPost post) {

        return post.sender() == null ? escape(post.headline()) : escape(post.sender()) + ": " + escape(post.headline());
    }

    private static Map<String, Object> section(String markdown) {

        return Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", markdown));
    }

    private static Map<String, Object> context(String markdown) {

        return Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", markdown)));
    }

    /** Slack's own three-character escape set. Order matters: the ampersand goes first or it re-escapes the others. */
    private static String escape(String raw) {

        return raw == null ? "" : raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
