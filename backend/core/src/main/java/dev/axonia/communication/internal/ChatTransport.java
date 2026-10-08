package dev.axonia.communication.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.axonia.communication.ChatFailureKind;
import dev.axonia.communication.MessageType;
import lombok.RequiredArgsConstructor;

/**
 * The one outbound POST. The URL is re-checked against the allowlist here and not only where it was saved, because a
 * row written by an older deploy is not something a newer one may trust, and because this is the last point before a
 * request leaves the network. The same {@link URI} instance that was checked is the one handed to {@link RestClient} -
 * never a string rebuilt from it - so there is exactly one parser's opinion of where the authority ends, not two that
 * could disagree.
 *
 * <p>
 * Connect and read timeouts come from {@code spring.http.clients.*} through the auto-configured builder. That is
 * load-bearing rather than incidental: every drain worker holds a database connection for the whole duration of its
 * send, so an unresponsive tenant endpoint has to cost one row and a retry, never a stalled pass. A statically built
 * {@code RestClient.builder()} bypasses that configuration and has no timeout at all. {@link CommunicationConfig} also
 * overrides the request factory's redirect policy to never follow one - see that class's javadoc for why the SSRF
 * allowlist below is worthless against a host that answers with a redirect.
 *
 * <p>
 * Nothing here spends from {@code SendPacing}'s budget, and nothing here throws {@code MailAccountRefusedException}.
 * The shared allowance exists because every organization's mail leaves through one provider account; a POST to a
 * tenant's own webhook spends none of it, and one community's revoked URL is not a reason to pause an organization's
 * whole pass.
 */
@Component
@RequiredArgsConstructor
class ChatTransport {

    private final RestClient chatRestClient;
    private final ChatPlatforms platforms;
    private final ChatPostBuilders posts;

    void post(URI url, MessageType type, Map<String, Object> facts) {

        post(url, posts.build(type, facts));
    }

    /**
     * The validate-render-POST shared by every caller, including {@link ChatChannelProbeAdapter}'s fixed test post -
     * which has no {@link MessageType} and no facts to build one from, only a {@link ChatPost} it already built itself.
     * {@link #post(URI, MessageType, Map)} is this method plus the one extra step of turning frozen facts into that
     * {@link ChatPost}.
     */
    void post(URI url, ChatPost post) {

        var platform = platforms.platformFor(url.toString())
                .orElseThrow(() -> new ChatSendException(ChatFailureKind.GONE,
                        "Stored webhook URL is not one axonia posts to: " + url.getHost()));
        var payload = platform.payload(post);
        try {
            chatRestClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve()
                    .onStatus(status -> status.value() >= 400, (request, response) -> {
                        throw new ChatSendException(kindOf(response.getStatusCode().value(), bodyOf(response)),
                                response.getStatusCode() + " from " + url.getHost());
                    }).toBodilessEntity();
        } catch (RestClientException networkFailure) {
            throw new ChatSendException(ChatFailureKind.TRANSIENT, networkFailureMessage(url, networkFailure));
        }
    }

    /**
     * Never {@code networkFailure.getMessage()}. Spring's {@code ResourceAccessException} - the connect-timeout /
     * connection-reset case the 2s/10s ceilings exist to produce - carries a message of the shape
     * {@code "I/O error on POST request for \"<uri>\": <ioMessage>"}: the QUERY is stripped, but the PATH is not, and a
     * Slack or Teams webhook's entire credential lives in the path. This is the one failure shape that reaches
     * {@link DrainWorkers}, {@code ChatDestinationsPort#recordFailure} and the leader-visible {@code chat_last_error}
     * column, so a secret here is not a log line that scrolls away - it is a row a personal-data footprint already
     * tracks. Naming the host, the same restraint the {@code onStatus} arm above already uses, says everything an
     * operator needs without ever risking the one thing that must never leave the URL.
     */
    private static String networkFailureMessage(URI url, RestClientException networkFailure) {

        var rootCause = networkFailure.getRootCause();
        var detail = rootCause == null
                ? networkFailure.getClass().getSimpleName()
                : rootCause.getClass().getSimpleName();
        return detail + " calling " + url.getHost();
    }

    /**
     * A 404, a 410, or Slack's own {@code invalid_token} means the webhook is revoked and retrying it four more times
     * is theatre. Everything else - a 429, a 5xx, a timeout - is ordinary weather.
     */
    private static ChatFailureKind kindOf(int status, String body) {

        var revoked = status == 404 || status == 410 || body.toLowerCase(Locale.ROOT).contains("invalid_token")
                || body.toLowerCase(Locale.ROOT).contains("no_service");
        return revoked ? ChatFailureKind.GONE : ChatFailureKind.TRANSIENT;
    }

    /** Defensive: an error body that cannot be read says nothing about which failure shape this was. */
    private static String bodyOf(ClientHttpResponse response) {

        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }
}
