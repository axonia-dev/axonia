package dev.axonia;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Counts and times every call this application's own code makes to somebody else's server.
 *
 * <p>
 * "Its own code" is the honest boundary, not a hedge. Libraries make calls of their own that this never sees and should
 * not try to wrap: Spring Security's JWT decoders fetch OIDC discovery and JWKS from the issuer over their own
 * {@code RestOperations} - both the resource server's autoconfigured decoder and any decoder an edition builds with
 * {@code JwtDecoders.fromIssuerLocation} - and the OTLP exporter, the Redis client and the JDBC driver are all outbound
 * too. Instrumenting a framework's internal client would mean owning its construction, which is a worse trade than
 * naming the limit here.
 *
 * <p>
 * The failure this exists for is a vendor that starts refusing: a revoked API key, an SMTP account over its daily cap,
 * a directory credential that expired. None of those announce themselves. They surface as a member who never got a
 * roundup, a chat post that never arrived, or a person page missing its groups - each noticed days later by whoever
 * happened to look, if anyone did.
 *
 * <p>
 * One component rather than a private helper inside each adapter. Several adapters share each HTTP client, besides the
 * SMTP transport, the object store and the avatar mirror, so a helper per adapter would be many near-identical copies
 * of the same try/catch, each with its own test under a 100% line rule, and each adding a constructor argument that
 * breaks every {@code @WebMvcTest} slice loading a controller that reaches it.
 *
 * <p>
 * Outbound HTTP is instrumented once, as a {@link ClientHttpRequestInterceptor} attached at each of the five
 * {@code RestClient} bean definitions, so no HTTP adapter is touched at all and no HTTP adapter can forget. The
 * integration is named at the bean definition rather than derived from the host that was called: {@code ChatTransport}
 * posts to webhook URLs the customer configured, so a tag read off a hostname would grow a new series per tenant and
 * quietly exhaust the metric ceiling. Naming it at the definition makes this meter's tag low-cardinality by
 * construction.
 *
 * <p>
 * That is a property of this meter and not of the process. Spring's own {@code http.client.requests} timer covers the
 * same five clients and does exactly what is ruled out above - its {@code client.name} is the raw host, as a
 * low-cardinality key value - so bounding it takes a {@code MeterFilter}, which {@code HttpClientMetricsConfig}
 * registers.
 *
 * <p>
 * The boundaries that are not a {@code RestClient} are wrapped at the adapter instead, through {@link #call},
 * {@link #run} and {@link #callChecked}: the S3 object store, the SMTP transport, and the raw
 * {@code java.net.http.HttpClient} that mirrors an avatar from an identity provider's {@code picture} claim.
 *
 * <p>
 * Neither meter carries a tenant. {@code organization_id} multiplies every series by the customer count and exhausts
 * the free tier's ceiling, at which point metrics are dropped with nobody told. The tenant dimension lives on traces
 * and logs, where cardinality is free.
 *
 * <p>
 * A failure logs one WARN naming the integration and the kind of failure - an exception's class name, or the HTTP
 * status. Never the exception's message and never a stack trace: on these paths a message routinely carries an SMTP
 * reply quoting the recipient, a response body, or the webhook URL whose path is itself the credential (see
 * {@code ChatTransport.networkFailureMessage}). The counters already say how often; the class name says what kind, and
 * that is as much as can be said here without risking the one thing that must not leave.
 *
 * <p>
 * Every meter for every integration is registered at construction rather than on that integration's first call. A
 * counter that springs into existence on first use emits no series at all while nothing is happening, which is exactly
 * when somebody is asking whether anything is happening.
 *
 * <p>
 * {@code axonia.integration.latency} is a health signal, not a service-level one, and its percentiles are not
 * interpretable as "how long one operation takes". Each integration's series pools every shape of call that integration
 * makes: {@code smtp} mixes {@code SmtpDelivery}'s one-off connection handshake with each message sent over it, and
 * {@code entra} pools a group lookup with a photo fetch. Read it for a step change against its own history. Do not
 * build a panel that calls it the time to send a message.
 *
 * <p>
 * Spring Boot's own {@code http.client.requests} timer covers the five HTTP clients too. These counters are additive
 * and deliberate: they are one stable, hand-named series spanning HTTP and non-HTTP boundaries alike, which is what the
 * dashboards and the alert rules read. Boot's timer, tagged by status, is where an operator drills in afterwards - on
 * every call including the folded ones, because {@code HttpClientMetricsConfig} rewrites only {@code client.name} and
 * leaves {@code status}, {@code outcome}, {@code method} and {@code exception} in place. What that tag loses is which
 * host was called, for any host {@code HttpClientMetricsConfig} does not know about, which in practice means every
 * tenant-configured Teams webhook.
 *
 * <p>
 * Lives in the root package rather than {@code config} because several modules reach it, and Spring Modulith treats
 * every direct subpackage of {@code dev.axonia} as a module.
 */
@Component
@Slf4j
public class IntegrationMetrics {

    private static final String CALLS = "axonia.integration.calls";

    private static final String LATENCY = "axonia.integration.latency";

    /**
     * Request attribute naming the one error status that is an answer rather than a refusal for this request, so the
     * interceptor counts it as a success and logs nothing. Set it with {@code .attribute(EXPECTED_STATUS,
     * HttpStatus.NOT_FOUND)} on the {@code RestClient} call, alongside whatever already turns that status into the
     * answer.
     *
     * <p>
     * It exists because {@code onStatus} cannot do this job: the interceptor runs below {@code retrieve()}, so it sees
     * the raw response whatever the call site does with it afterwards. Without a way to say so at the call, Graph's 404
     * for a member who has never set a photo - a per-member, per-sync occurrence - would count as a failed call and
     * emit a WARN every time, which is thousands of lines and a metric floor for a large directory, and an operator who
     * learns to scroll past {@code integration=entra}.
     *
     * <p>
     * Deliberately one status rather than a set, and deliberately per request rather than per client: an expectation
     * that covers every call of a client is not an expectation, it is a hole. A call site with a second genuinely
     * expected status should widen this then, with the case in front of it.
     *
     * <p>
     * <strong>Never a 5xx.</strong> A 5xx is the other server having broken, which is never an answer to anything, so
     * naming one here would silence a real outage on the one meter meant to show it. Nothing in the type system stops a
     * caller writing it, so the interceptor ignores a 5xx expectation outright rather than trusting the rule to be
     * read: such a request is counted and logged exactly as if the attribute were absent.
     */
    public static final String EXPECTED_STATUS = "axonia.integration.expected-status";

    private final Map<Integration, IntegrationMeters> meters;

    public IntegrationMetrics(MeterRegistry registry) {

        this(registry, List.of());
    }

    @Autowired
    public IntegrationMetrics(MeterRegistry registry, List<MeterContribution> contributions) {

        this.meters = MeterVocabulary.join(Integration.open(),
                contributions.stream().flatMap(contribution -> contribution.integrations().stream()), Integration::tag)
                .stream().collect(Collectors.toUnmodifiableMap(Function.identity(),
                        integration -> new IntegrationMeters(registry, integration)));
    }

    /** Times one outbound call that answers with something, counts its outcome, and rethrows whatever it threw. */
    public <T> T call(Integration integration, Supplier<T> outboundCall) {

        return meters(integration).counted(outboundCall);
    }

    /** {@link #call} for a boundary whose call answers with nothing. */
    public void run(Integration integration, Runnable outboundCall) {

        meters(integration).counted(() -> {
            outboundCall.run();
            return null;
        });
    }

    /**
     * {@link #call} for a boundary whose call declares checked exceptions, which a {@link Supplier} cannot express.
     * {@code java.net.http.HttpClient#send} is the only such boundary here, and it declares two.
     *
     * <p>
     * {@code refusal} is what makes this as sharp as the interceptor rather than weaker than it. A raw HTTP client
     * hands a refusal back as an ordinary return value, so without it every 401 and 403 would count as a success and
     * this counter would read perfectly healthy through exactly the failure it exists for. It answers a short,
     * non-personal description of the refusal - a status, never a URL, a body or an address - or {@code null} when the
     * answer is an answer. Deciding which is which is the adapter's job, because only the adapter knows what its own
     * caller treats as a finding: the same knowledge {@link #EXPECTED_STATUS} carries down to the interceptor,
     * expressed directly here because there is no interceptor to carry it to.
     */
    public <T> T callChecked(Integration integration, CheckedCall<T> outboundCall, Function<T, String> refusal)
            throws IOException, InterruptedException {

        return meters(integration).countedChecked(outboundCall, refusal);
    }

    /**
     * The interceptor to attach to one {@code RestClient} bean, carrying the name that bean's calls are counted under.
     *
     * <p>
     * An interceptor rather than a try/catch around {@code retrieve()}: it sees the {@link IOException} and the error
     * status as separate outcomes of the same call, it sees them for every call site of that client rather than the
     * ones somebody remembered to wrap, and it sits below whatever {@code onStatus} handler a call site installed, so
     * what it counts is what the server actually answered.
     *
     * <p>
     * That last property is also its blind spot, and {@link #EXPECTED_STATUS} is how a call site closes it.
     */
    public ClientHttpRequestInterceptor interceptor(Integration integration) {

        return meters(integration)::intercept;
    }

    private IntegrationMeters meters(Integration integration) {

        return MeterVocabulary.registered(meters, integration, integration.tag());
    }

    /** An outbound integration, carrying the {@code integration} tag its own alert rule matches on. */
    public record Integration(String tag) {

        public static final Integration ENTRA = new Integration("entra");

        public static final Integration CHAT = new Integration("chat");

        public static final Integration S3 = new Integration("s3");

        public static final Integration SMTP = new Integration("smtp");

        /**
         * Mirroring a member's photo from an identity provider's {@code picture} claim, over a raw
         * {@code java.net.http.HttpClient} rather than a {@code RestClient}. One fixed tag for what is in principle
         * three hosts, because {@code axonia.avatar.allowed-hosts} is a fixed list an operator sets, not something a
         * tenant can grow.
         */
        public static final Integration AVATAR = new Integration("avatar");

        /**
         * Fetching an MCP client's Client ID Metadata Document. Fixed tag: the hosts are the operator's
         * {@code axonia.mcp.oauth.trusted-client-domains}, never typed by a tenant.
         */
        public static final Integration CLIENT_METADATA = new Integration("client-metadata");

        public static final Integration AZURE_BLOB = new Integration("azure-blob");

        public Integration {

            MeterVocabulary.requireTag(tag);
        }

        static List<Integration> open() {

            return List.of(ENTRA, CHAT, S3, SMTP, AVATAR, CLIENT_METADATA, AZURE_BLOB);
        }
    }

    /**
     * An outbound call that fails the way {@code java.net.http.HttpClient#send} does: with a checked exception, and
     * with more than one of them, which no {@code java.util.function} type can express.
     */
    @FunctionalInterface
    public interface CheckedCall<T> {

        T get() throws IOException, InterruptedException;
    }

    /**
     * Holds one integration's two counters and its timer rather than the registry they came from, following
     * {@code RateLimiter} and {@code SweepHeartbeat}: meters resolved once are what a per-call path wants anyway, and
     * it keeps the injected {@link MeterRegistry} out of any field, which SpotBugs raises {@code EI_EXPOSE_REP2} on.
     */
    private static final class IntegrationMeters {

        private final String tag;

        private final Counter successes;

        private final Counter failures;

        private final Timer latency;

        private IntegrationMeters(MeterRegistry registry, Integration integration) {

            this.tag = integration.tag();
            this.successes = Counter.builder(CALLS).tag("integration", tag).tag("outcome", "success")
                    .register(registry);
            this.failures = Counter.builder(CALLS).tag("integration", tag).tag("outcome", "failure").register(registry);
            this.latency = Timer.builder(LATENCY).tag("integration", tag).register(registry);
        }

        private <T> T counted(Supplier<T> outboundCall) {

            var startedAt = System.nanoTime();
            try {
                var answer = outboundCall.get();
                successes.increment();
                return answer;
            } catch (RuntimeException e) {
                failed(e.getClass().getName());
                throw e;
            } finally {
                latency.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            }
        }

        /**
         * Any status the HTTP client itself calls an error - 4xx as well as 5xx - is a failure, because refusal is
         * overwhelmingly a 4xx: a revoked key answers 401, a de-scoped one 403, an account over its cap 429. Counting
         * only 5xx would leave this reading perfectly healthy through the whole class of failure it was built for.
         *
         * <p>
         * It is also what almost every call site here experiences: {@code retrieve()} turns a non-2xx into a thrown
         * exception unless the call site opted out. Some have. {@code EntraPhotoSource} treats Graph's 404 as "this
         * person has no photo", which happens once per member per sync and would otherwise put a floor under
         * {@code entra}'s failure rate and a WARN in the log for every member of a large directory who never set one;
         * it says so with {@link #EXPECTED_STATUS} and is counted as a success. An idempotent DELETE that treats 404 as
         * done, and adding an already-present member, which treats 409 as done, are left counting as failures
         * deliberately: each happens at most once per erasure or per invitation, so the noise is negligible, and a
         * blanket exemption would also hide a 404 that meant the identity provider had lost the record.
         *
         * <p>
         * Absent that attribute the interceptor cannot tell, because whether a 404 was expected is a fact about the
         * caller rather than about the response. {@link #counted}, which sits at an adapter and does know, classifies
         * on that knowledge directly instead.
         */
        private ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {

            var startedAt = System.nanoTime();
            try {
                var response = execution.execute(request, body);
                if (refused(response.getStatusCode(), request)) {
                    failed("HTTP " + response.getStatusCode().value());
                } else {
                    successes.increment();
                }
                return response;
            } catch (IOException | RuntimeException e) {
                failed(e.getClass().getName());
                throw e;
            } finally {
                latency.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            }
        }

        private <T> T countedChecked(CheckedCall<T> outboundCall, Function<T, String> refusal)
                throws IOException, InterruptedException {

            var startedAt = System.nanoTime();
            try {
                var answer = outboundCall.get();
                var refused = refusal.apply(answer);
                if (refused == null) {
                    successes.increment();
                } else {
                    failed(refused);
                }
                return answer;
            } catch (IOException | InterruptedException | RuntimeException e) {
                failed(e.getClass().getName());
                throw e;
            } finally {
                latency.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            }
        }

        private static boolean refused(HttpStatusCode status, HttpRequest request) {

            return status.isError() && !expected(status, request);
        }

        /**
         * Compared by numeric value, so a call site naming 404 matches whatever {@code HttpStatusCode} carried it. A
         * 5xx expectation is rejected here rather than merely discouraged in {@link #EXPECTED_STATUS}'s javadoc: the
         * whole value of that attribute is that it exempts something that was never a failure, and a 5xx always is.
         */
        private static boolean expected(HttpStatusCode status, HttpRequest request) {

            return request.getAttributes().get(EXPECTED_STATUS) instanceof HttpStatusCode expected
                    && expected.value() == status.value() && !expected.is5xxServerError();
        }

        private void failed(String kind) {

            failures.increment();
            log.warn("Outbound call failed: integration={} failure={}", tag, kind);
        }
    }
}
