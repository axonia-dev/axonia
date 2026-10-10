package dev.axonia.communication.adapter.out.chat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsPlatformTest {

    private final TeamsPlatform sut = new TeamsPlatform();

    private static ChatPost post(String headline, String body) {

        return ChatPost.builder().sender("Backend Guild").headline(headline).whenLine("Held 1 September 2026 at 16:00")
                .body(body).details(Map.of("Attended", "24")).actionLabel("View the wrap-up")
                .actionUrl("https://axonia.example/s/1").build();
    }

    @Test
    void payload_isAnAdaptiveCardAttachment() {

        var payload = sut.payload(post("Event sourcing", null));

        assertThat(payload.get("type")).isEqualTo("message");
        @SuppressWarnings("unchecked")
        var attachments = (List<Map<String, Object>>) payload.get("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).get("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        @SuppressWarnings("unchecked")
        var card = (Map<String, Object>) attachments.get(0).get("content");
        assertThat(card.get("type")).isEqualTo("AdaptiveCard");
    }

    /** The one-action rule pinned on this platform too: {@link SlackPlatformTest#payload_carriesExactlyOneAction}. */
    @Test
    void payload_carriesExactlyOneAction() {

        var payload = sut.payload(post("Event sourcing", null));

        @SuppressWarnings("unchecked")
        var attachments = (List<Map<String, Object>>) payload.get("attachments");
        @SuppressWarnings("unchecked")
        var card = (Map<String, Object>) attachments.get(0).get("content");
        @SuppressWarnings("unchecked")
        var actions = (List<Map<String, Object>>) card.get("actions");

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).get("type")).isEqualTo("Action.OpenUrl");
        assertThat(actions.get(0).get("title")).isEqualTo("View the wrap-up");
        assertThat(actions.get(0).get("url")).isEqualTo("https://axonia.example/s/1");
    }

    @Test
    void payload_escapesTheMarkdownAdaptiveCardsDoRender() {

        var payload = sut.payload(post("**not bold**", "[label](https://evil.test)"));

        assertThat(payload.toString()).doesNotContain("[label](https://evil.test)");
        assertThat(payload.toString()).contains("\\*\\*not bold\\*\\*".replace("\\\\", "\\"));
    }

    @Test
    void payload_escapesTheBackslashFirstSoAPriorEscapeCannotReviveALink() {

        var payload = sut.payload(post("Event sourcing", "\\[Click here\\](https://evil.test)"));

        var threeBackslashes = "\\".repeat(3);
        assertThat(payload.toString())
                .contains(threeBackslashes + "[Click here" + threeBackslashes + "](https://evil.test)");
    }

    @Test
    void payload_omitsTheSenderBlockRatherThanAnEmptyBolderLineWhenThereIsNoSender() {

        var post = ChatPost.builder().headline("Event sourcing").whenLine("Held 1 September 2026 at 16:00")
                .actionLabel("View the wrap-up").actionUrl("https://axonia.example/s/1").build();

        var payload = sut.payload(post);

        @SuppressWarnings("unchecked")
        var attachments = (List<Map<String, Object>>) payload.get("attachments");
        @SuppressWarnings("unchecked")
        var card = (Map<String, Object>) attachments.get(0).get("content");
        @SuppressWarnings("unchecked")
        var body = (List<Map<String, Object>>) card.get("body");
        assertThat(body).noneMatch(block -> "Bolder".equals(block.get("weight")));
    }
}
