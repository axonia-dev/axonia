package dev.axonia.communication.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackPlatformTest {

    private final SlackPlatform sut = new SlackPlatform();

    private static ChatPost post(String headline, String body) {

        return ChatPost.builder().sender("Backend Guild").headline(headline).whenLine("Held 1 September 2026 at 16:00")
                .body(body).details(Map.of("Attended", "24")).actionLabel("View the wrap-up")
                .actionUrl("https://axonia.example/s/1").build();
    }

    @Test
    void payload_carriesAFallbackTextBesideTheBlocks() {

        var payload = sut.payload(post("Event sourcing", "We agreed to spike it."));

        assertThat(payload.get("text")).isEqualTo("Backend Guild: Event sourcing");
        assertThat(payload).containsKey("blocks");
    }

    @Test
    void payload_carriesExactlyOneAction() {

        var payload = sut.payload(post("Event sourcing", null));

        @SuppressWarnings("unchecked")
        var blocks = (List<Map<String, Object>>) payload.get("blocks");
        var actionsBlocks = blocks.stream().filter(block -> "actions".equals(block.get("type"))).toList();
        assertThat(actionsBlocks).hasSize(1);
        @SuppressWarnings("unchecked")
        var elements = (List<Map<String, Object>>) actionsBlocks.get(0).get("elements");
        assertThat(elements).hasSize(1);
        assertThat(elements.get(0).get("type")).isEqualTo("button");
        assertThat(elements.get(0).get("url")).isEqualTo("https://axonia.example/s/1");
    }

    @Test
    void payload_escapesMemberTypedTextSoATitleCannotRenderAsALink() {

        var payload = sut.payload(post("<https://evil.test|Click here>", "a & b <c>"));

        assertThat(payload.toString()).doesNotContain("<https://evil.test|");
        assertThat(payload.toString()).contains("&lt;https://evil.test|Click here&gt;");
        assertThat(payload.toString()).contains("a &amp; b &lt;c&gt;");
    }

    @Test
    void payload_omitsTheSenderLineRatherThanABareHeadingWhenThereIsNoSender() {

        var post = ChatPost.builder().headline("Event sourcing").whenLine("Held 1 September 2026 at 16:00")
                .actionLabel("View the wrap-up").actionUrl("https://axonia.example/s/1").build();

        var payload = sut.payload(post);

        assertThat(payload.get("text")).isEqualTo("Event sourcing");
        @SuppressWarnings("unchecked")
        var blocks = (List<Map<String, Object>>) payload.get("blocks");
        @SuppressWarnings("unchecked")
        var headerText = (Map<String, Object>) blocks.get(0).get("text");
        assertThat(headerText.get("text")).isEqualTo("Event sourcing");
    }
}
