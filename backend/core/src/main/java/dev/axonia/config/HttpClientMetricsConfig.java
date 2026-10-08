package dev.axonia.config;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Keeps Spring's own outbound HTTP timer inside the cardinality rule, which it does not obey by itself.
 *
 * <p>
 * {@code RestClientObservationAutoConfiguration} instruments every {@code RestClient} built from the auto-configured
 * builder, which is all five of them, and
 * {@code DefaultClientRequestObservationConvention.clientName(ClientRequestObservationContext)} answers
 * {@code context.getCarrier().getURI().getHost()} - the raw host that was called - as a LOW-cardinality key value, so
 * it becomes a dimension of the meter rather than of the span. One of those five clients posts to webhook URLs a
 * customer configured, and one of the two platforms it posts to is not a fixed host: {@code TeamsPlatform} claims any
 * host under {@code .webhook.office.com}, {@code .logic.azure.com} or {@code .environment.api.powerplatform.com}, and
 * the segment ahead of those suffixes varies by tenant and region. So {@code client.name} grows one series per customer
 * webhook host, without bound, on a free tier with a fixed active series ceiling.
 *
 * <p>
 * {@code IntegrationMetrics} avoids exactly this by naming the integration at the bean definition, and Boot's
 * {@code max-uri-tags} ceiling does not help here: it bounds {@code uri}, and nothing bounds {@code client.name}.
 *
 * <p>
 * So the tag is kept for the hosts this application's own configuration points a client at, and folded to {@code other}
 * for everything else. Folding rather than dropping keeps the aggregate readable: every other tag on the series
 * survives, so {@code other} is still broken down by {@code status}, {@code outcome}, {@code method} and
 * {@code exception}, and with Slack's one fixed host on the list it reads as Teams webhook traffic. The same traffic is
 * also counted under {@code axonia.integration.calls{integration="chat"}}, whose name no tenant can influence.
 *
 * <p>
 * A host added later and neither named here nor contributed as an {@link OutboundHost} reads as {@code other} rather
 * than as itself, which loses detail and cannot lose the bound. That is the direction this has to fail in.
 */
@Configuration
class HttpClientMetricsConfig {

    static final String CLIENT_NAME = "client.name";

    static final String OTHER = "other";

    /**
     * The meter name is read from {@link ObservationProperties} rather than written out, because
     * {@code management.observations.http.client.requests.name} renames it and a filter matching a name nothing emits
     * would leave the tag unbounded while reporting nothing wrong.
     */
    @Bean
    MeterFilter boundedOutboundClientName(ObservationProperties observations, List<OutboundHost> configured) {

        return new BoundedClientName(observations.getHttp().getClient().getRequests().getName(),
                knownHosts(configured));
    }

    /**
     * Every outbound host this repository names. {@code graph.microsoft.com} and {@code login.microsoftonline.com} are
     * literals in {@code EntraIdpConfig} and {@code hooks.slack.com} one in {@code SlackPlatform} - none of the three
     * moves per profile, so they stay fixed here. The hosts that do move per profile (an operator's own identity
     * provider, an external provider's sandbox versus its live API) are contributed by their owning module as an
     * {@link OutboundHost} bean built from that module's own property, so this always names whichever host this running
     * instance actually calls, and names none for a module the build does not contain. Pinning either as a literal here
     * would go stale exactly when the owning property's value did - which is what made every identity provider call
     * read as {@code other} once already.
     *
     * <p>
     * Derived leniently, deliberately: this bean sits on the startup path of every profile, and a metrics tag is not
     * worth a boot failure. {@link #hostOf} swallows the {@code IllegalArgumentException} a malformed property value
     * throws from {@link URI#create}, a blank or scheme-less value parses to a {@code null} host without throwing at
     * all, and {@link URI#getHost()} itself answers {@code null} for a syntactically valid authority whose hostname is
     * not - an underscore, for one. Any of those simply contributes no host, rather than failing every profile's
     * startup over one property this filter only ever reads, never depends on. {@link Collectors#toUnmodifiableSet()}
     * rather than {@link Set#of} for the same reason on the other axis: two contributions resolving to the same host,
     * or to one of the three fixed literals, is a duplicate value, not a startup failure.
     */
    private static Set<String> knownHosts(List<OutboundHost> configured) {

        return Stream
                .concat(Stream.of("graph.microsoft.com", "login.microsoftonline.com", "hooks.slack.com"),
                        configured.stream().map(host -> hostOf(host.baseUrl())))
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    private static String hostOf(String baseUrl) {

        try {
            return URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record BoundedClientName(String meter, Set<String> knownHosts) implements MeterFilter {

        /**
         * {@code withTag} and never {@code replaceTags}. The convention emits six low-cardinality keys -
         * {@code client.name}, {@code exception}, {@code method}, {@code outcome}, {@code status} and {@code uri} - and
         * {@code Meter.Id.replaceTags} is documented as answering an id with ONLY the tags handed to it, so folding
         * through it would collapse every Teams delivery into one untagged timer and take {@code status} and
         * {@code outcome} with it. A 500 storm against a customer's endpoint would then be indistinguishable from
         * success on the exact meter this filter exists to keep usable. {@code withTag} overwrites the value for a key
         * that is already there and leaves the rest alone.
         */
        @Override
        public Meter.Id map(Meter.Id id) {

            var host = id.getTag(CLIENT_NAME);
            if (!meter.equals(id.getName()) || host == null || knownHosts.contains(host)) {
                return id;
            }
            return id.withTag(Tag.of(CLIENT_NAME, OTHER));
        }
    }
}
