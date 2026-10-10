package dev.axonia.shared.api.metrics;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.axonia.shared.api.metrics.IntegrationMetrics.Integration;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final IntegrationMetrics metrics = new IntegrationMetrics(registry);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private final Logger metricsLogger = (Logger) LoggerFactory.getLogger(IntegrationMetrics.class);

    @BeforeEach
    void captureLogs() {

        logs.start();
        metricsLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {

        metricsLogger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void countsACallThatAnswered() {

        // when / then
        assertThat(metrics.call(Integration.CHAT, () -> "txn_1")).isEqualTo("txn_1");

        assertThat(calls("chat", "success")).isEqualTo(1.0);
    }

    @Test
    void countsACallThatFailedAndRethrowsIt() {

        // when / then
        assertThatThrownBy(() -> metrics.call(Integration.CHAT, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls("chat", "failure")).isEqualTo(1.0);
    }

    @Test
    void countsAVoidCallThatReturned() {

        // given
        var ran = new AtomicBoolean();

        // when
        metrics.run(Integration.S3, () -> ran.set(true));

        // then
        assertThat(ran).isTrue();
        assertThat(calls("s3", "success")).isEqualTo(1.0);
    }

    @Test
    void countsAVoidCallThatFailedAndRethrowsIt() {

        // when / then
        assertThatThrownBy(() -> metrics.run(Integration.S3, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls("s3", "failure")).isEqualTo(1.0);
    }

    /** Latency is what says an integration has gone slow rather than gone down, so a failed call is timed too. */
    @Test
    void timesACallWhetherItAnsweredOrFailed() {

        // when / then
        metrics.call(Integration.SMTP, () -> "sent");
        assertThatThrownBy(() -> metrics.run(Integration.SMTP, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(latencyCount("smtp")).isEqualTo(2L);
    }

    @Test
    void countsAnInterceptedRequestTheServerAnswered() throws IOException {

        // when
        answerWith(HttpStatus.OK);

        // then
        assertThat(calls("entra", "success")).isEqualTo(1.0);
        assertThat(latencyCount("entra")).isEqualTo(1L);
    }

    /**
     * The shape of a revoked or de-scoped credential, and the reason a 4xx counts as a failure: every way a vendor
     * refuses arrives as a 4xx, so counting only 5xx would leave this reading healthy through the whole class of
     * failure the counter exists for.
     */
    @Test
    void countsAnInterceptedRequestTheServerRefused() throws IOException {

        // when
        answerWith(HttpStatus.UNAUTHORIZED);

        // then
        assertThat(calls("entra", "failure")).isEqualTo(1.0);
    }

    @Test
    void countsAnInterceptedRequestTheServerBrokeOn() throws IOException {

        // when
        answerWith(HttpStatus.INTERNAL_SERVER_ERROR);

        // then
        assertThat(calls("entra", "failure")).isEqualTo(1.0);
    }

    @Test
    void countsAnInterceptedRequestThatNeverGotAnAnswerAndRethrowsIt() {

        // when / then
        assertThatThrownBy(() -> metrics.interceptor(Integration.CHAT).intercept(new MockClientHttpRequest(),
                new byte[0], (request, body) -> {
                    throw new IOException("connection reset");
                })).isInstanceOf(IOException.class);

        assertThat(calls("chat", "failure")).isEqualTo(1.0);
        assertThat(latencyCount("chat")).isEqualTo(1L);
    }

    /**
     * The hole a lazily-registered counter leaves. An integration nobody has called yet would emit no series at all, so
     * a dashboard reading its rate would show a gap where it should show a flat zero, and the panel would be
     * indistinguishable from one whose metric had been renamed away.
     */
    @Test
    void publishesBothCountersAndATimerForAnIntegrationNothingHasCalled() {

        // when / then
        assertThat(calls("entra", "success")).isZero();
        assertThat(calls("entra", "failure")).isZero();
        assertThat(latencyCount("entra")).isZero();
    }

    /**
     * Asserted against {@link Integration#open()} rather than a hand-written list, so an open integration cannot be
     * declared without being registered: the map this reads is built from the same list.
     */
    @Test
    void watchesEveryIntegrationFromProcessStart() {

        // when / then
        assertThat(registry.get("axonia.integration.latency").timers())
                .extracting(timer -> timer.getId().getTag("integration"))
                .containsExactlyInAnyOrderElementsOf(Integration.open().stream().map(Integration::tag).toList());
    }

    /** The strings the alert rules match on, pinned so a rename cannot pass silently. */
    @Test
    void carriesTheContractTagOfEveryIntegration() {

        // when / then
        assertThat(Integration.open().stream().map(Integration::tag)).containsExactly("entra", "chat", "s3", "smtp",
                "avatar", "client-metadata", "azure-blob");
    }

    @Test
    void refusesAnIntegrationNobodyContributed() {

        assertThatThrownBy(() -> metrics.run(new Integration("unregistered"), () -> {
        })).hasMessageStartingWith("No meter is registered for unregistered");
    }

    /**
     * A tenant tag would multiply every one of these series by the customer count and silently exhaust the export
     * ceiling, at which point metrics are dropped with nobody told. The tenant dimension lives on traces and logs.
     */
    @Test
    void tagsNeitherMeterWithATenant() {

        // when
        metrics.call(Integration.CHAT, () -> "txn_1");

        // then
        assertThat(registry.get("axonia.integration.calls").counters())
                .allSatisfy(counter -> assertThat(counter.getId().getTags()).extracting(Tag::getKey)
                        .containsExactlyInAnyOrder("integration", "outcome"));
        assertThat(registry.get("axonia.integration.latency").timers()).allSatisfy(
                timer -> assertThat(timer.getId().getTags()).extracting(Tag::getKey).containsExactly("integration"));
    }

    @Test
    void countsACheckedCallThatAnswered() throws Exception {

        // when / then
        assertThat(metrics.callChecked(Integration.AVATAR, () -> "bytes", answer -> null)).isEqualTo("bytes");

        assertThat(calls("avatar", "success")).isEqualTo(1.0);
    }

    /**
     * The reason {@link IntegrationMetrics#callChecked} exists at all: {@code java.net.http.HttpClient#send} fails with
     * a checked {@link IOException}, which no {@code Supplier} can declare, and that failure - the host gone
     * unreachable - is the one this counter is there to catch.
     */
    @Test
    void countsACheckedCallThatFailedAndRethrowsIt() {

        // when / then
        assertThatThrownBy(() -> metrics.callChecked(Integration.AVATAR, () -> {
            throw new IOException("connection reset");
        }, answer -> null)).isInstanceOf(IOException.class);

        assertThat(calls("avatar", "failure")).isEqualTo(1.0);
        assertThat(latencyCount("avatar")).isEqualTo(1L);
    }

    @Test
    void logsOneWarningNamingTheIntegrationAndTheKindOfFailure() {

        // when / then
        assertThatThrownBy(() -> metrics.call(Integration.CHAT, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("integration=chat").contains("IllegalStateException");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    /**
     * The counters say how often; the exception's class name says what kind; the message is deliberately never touched,
     * because on these paths it routinely carries an SMTP reply quoting the recipient, a response body, or a webhook
     * URL whose path is the credential itself.
     */
    @Test
    void neverPutsAnExceptionMessageInTheLog() {

        // when / then
        assertThatThrownBy(() -> metrics.call(Integration.CHAT, () -> {
            throw new IllegalStateException("https://hooks.slack.com/services/T000/B000/the-secret-bit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(logs.list).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).doesNotContain("the-secret-bit"));
    }

    /**
     * The narrow exemption. A call site that has declared one error status to be an answer gets it counted as a success
     * and logged not at all - an {@code onStatus} handler cannot do this, because the interceptor runs below
     * {@code retrieve()} and sees the raw response whatever the call site does with it afterwards.
     */
    @Test
    void countsAnExpectedErrorStatusAsAnAnsweredCallAndLogsNothing() throws IOException {

        // when
        answerWith(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND);

        // then
        assertThat(calls("entra", "success")).isEqualTo(1.0);
        assertThat(calls("entra", "failure")).isZero();
        assertThat(logs.list).isEmpty();
    }

    /** The marker names one status, so it exempts that status and nothing else. */
    @Test
    void stillCountsAnUnexpectedErrorStatusOnARequestThatDeclaredADifferentOne() throws IOException {

        // when
        answerWith(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // then
        assertThat(calls("entra", "failure")).isEqualTo(1.0);
        assertThat(logs.list).hasSize(1);
    }

    /** The same 404 with nothing declared: the default is unchanged, and a 4xx is a failure. */
    @Test
    void countsAnUndeclaredNotFoundAsAFailure() throws IOException {

        // when
        answerWith(HttpStatus.NOT_FOUND);

        // then
        assertThat(calls("entra", "failure")).isEqualTo(1.0);
    }

    /**
     * A raw HTTP client hands a refusal back as an ordinary return value, so without this the counter would read 100
     * percent success while the host answered 403 to everything. The kind the adapter names reaches the log.
     */
    @Test
    void countsAnAnswerTheAdapterCallsARefusalAsAFailedCall() throws Exception {

        // when / then
        assertThat(metrics.callChecked(Integration.AVATAR, () -> "403 body", answer -> "HTTP 403"))
                .isEqualTo("403 body");

        assertThat(calls("avatar", "failure")).isEqualTo(1.0);
        assertThat(calls("avatar", "success")).isZero();
        assertThat(logs.list).singleElement().satisfies(
                event -> assertThat(event.getFormattedMessage()).contains("integration=avatar").contains("HTTP 403"));
    }

    /**
     * A 5xx is the other server having broken, which is never an answer, so naming one would silence a real outage on
     * the one meter meant to show it. Refused in code rather than only in the javadoc, because nothing in the type
     * system stops a caller writing it.
     */
    @Test
    void ignoresAnExpectedStatusThatIsA5xx() throws IOException {

        // when
        answerWith(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);

        // then
        assertThat(calls("entra", "failure")).isEqualTo(1.0);
        assertThat(logs.list).hasSize(1);
    }

    private ClientHttpResponse answerWith(HttpStatus status) throws IOException {

        return metrics.interceptor(Integration.ENTRA).intercept(new MockClientHttpRequest(), new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], status));
    }

    private ClientHttpResponse answerWith(HttpStatus status, HttpStatus expected) throws IOException {

        var request = new MockClientHttpRequest();
        request.getAttributes().put(IntegrationMetrics.EXPECTED_STATUS, expected);
        return metrics.interceptor(Integration.ENTRA).intercept(request, new byte[0],
                (ignored, body) -> new MockClientHttpResponse(new byte[0], status));
    }

    private double calls(String integration, String outcome) {

        return registry.get("axonia.integration.calls").tag("integration", integration).tag("outcome", outcome)
                .counter().count();
    }

    private long latencyCount(String integration) {

        return registry.get("axonia.integration.latency").tag("integration", integration).timer().count();
    }
}
