package dev.axonia.communication.internal;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.ChatPlatformName;
import dev.axonia.communication.ChatWebhooks;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPlatformsTest {

    private final ChatPlatforms sut = new ChatPlatforms(List.of(new SlackPlatform(), new TeamsPlatform()));

    @Test
    void platformOf_recognizesASlackIncomingWebhook() {

        assertThat(sut.platformOf("https://hooks.slack.com/services/T000/B000/xxxx")).contains(ChatPlatformName.SLACK);
    }

    /**
     * The host a flow built today hands a leader. Power Automate moved off {@code logic.azure.com} and retired it for
     * commercial tenants on 30 November 2025; the segment ahead of {@code environment} varies by tenant and region, and
     * the URL carries an explicit {@code :443} and a long query string, none of which the host check may trip over.
     */
    @Test
    void platformOf_recognizesACurrentPowerAutomateWorkflowUrl() {

        assertThat(sut.platformOf("https://e1b2c3.1f.environment.api.powerplatform.com:443/powerautomate/automations"
                + "/direct/workflows/abc/triggers/manual/paths/invoke?api-version=1&sig=xyz"))
                .contains(ChatPlatformName.TEAMS);
    }

    @Test
    void platformOf_recognizesALegacyPowerAutomateWorkflowUrl() {

        assertThat(sut.platformOf("https://prod-12.westeurope.logic.azure.com:443/workflows/abc/triggers/manual"))
                .contains(ChatPlatformName.TEAMS);
    }

    @Test
    void platformOf_recognizesALegacyOfficeConnector() {

        assertThat(sut.platformOf("https://contoso.webhook.office.com/webhookb2/abc@def/IncomingWebhook/ghi"))
                .contains(ChatPlatformName.TEAMS);
    }

    @Test
    void platformOf_rejectsPlainHttp() {

        assertThat(sut.platformOf("http://hooks.slack.com/services/T000/B000/xxxx")).isEmpty();
    }

    @Test
    void platformOf_rejectsAnyOtherHost() {

        assertThat(sut.platformOf("https://example.com/services/T000/B000/xxxx")).isEmpty();
    }

    @Test
    void platformOf_rejectsAHostThatMerelyEndsWithAnAllowedNameWithoutTheDot() {

        assertThat(sut.platformOf("https://evilhooks.slack.com.attacker.test/x")).isEmpty();
        assertThat(sut.platformOf("https://notlogic.azure.com.attacker.test/x")).isEmpty();
        assertThat(sut.platformOf("https://notenvironment.api.powerplatform.com.attacker.test/x")).isEmpty();
    }

    @Test
    void platformOf_rejectsALinkLocalOrLoopbackHost() {

        assertThat(sut.platformOf("https://169.254.169.254/latest/meta-data")).isEmpty();
        assertThat(sut.platformOf("https://localhost/x")).isEmpty();
    }

    @Test
    void platformOf_rejectsGarbage() {

        assertThat(sut.platformOf("not a url")).isEmpty();
        assertThat(sut.platformOf("")).isEmpty();
    }

    @Test
    void platformOf_rejectsAUrlCarryingCredentials() {

        assertThat(sut.platformOf("https://user:pass@hooks.slack.com/services/T000/B000/x")).isEmpty();
    }

    /**
     * {@link ChatWebhooks#platformOf} is a public port; every call site inside this codebase happens to guard against
     * {@code null} first, but nothing on the interface itself requires that, and {@code new URI(null)} throws
     * {@code NullPointerException} rather than the {@code URISyntaxException} every other malformed input already
     * produces {@code Optional.empty()} for.
     */
    @Test
    void platformOf_returnsEmptyRatherThanThrowingForANullUrl() {

        assertThat(sut.platformOf(null)).isEmpty();
    }
}
