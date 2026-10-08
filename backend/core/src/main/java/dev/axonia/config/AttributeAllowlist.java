package dev.axonia.config;

import java.util.Set;

/**
 * The complete set of span attribute keys permitted to leave this process.
 *
 * <p>
 * An allowlist rather than a denylist: an attribute introduced next year is excluded before anyone has to think about
 * it, and publishing one is a deliberate edit to this file. A denylist would have to predict the name of every field
 * that has not been written yet.
 *
 * <p>
 * Adding a key here is a decision about personal data. {@link ScrubbingSpanFilter} is what enforces it, on every span
 * every exporter receives; {@code TelemetryPersonalDataGuardTest} is the proof that the filter is installed on the
 * production path and behaves as claimed on the requests it drives. The guard is a sample, not a universal - it cannot
 * see a key that only some untested request emits, which is why the filter defaults to dropping rather than to keeping.
 */
public final class AttributeAllowlist {

    public static final Set<String> PERMITTED = Set.of(
            // Tenancy, both opaque, stamped by RequestTelemetryFilter.
            "organization_id", "user_id",
            // The server span as Spring Framework's DefaultServerRequestObservationConvention names it. On a server
            // span "uri" is the templated route, or one of that convention's placeholders where no handler matched,
            // and the request's own path is never on it - that arrives as "http.url", deliberately absent because a
            // path can carry a handle.
            //
            // On a CLIENT span the same key is whatever was passed to RestClient.uri(String), minus scheme and
            // authority only: extractPath strips ^https?://[^/]+ and nothing else, so BOTH the path and the query
            // string are exported verbatim. Interpolating a value into that argument therefore publishes it - which
            // is what GraphGroups.fetchGroupsPage documents avoiding. RestClient.uri(URI) records no
            // template at all and reports "none", so a call carrying anything runtime-derived should use it.
            "method", "uri", "status", "outcome", "exception",
            // Outbound calls: the host that was called, and nothing about the call's target beyond it. A webhook host
            // identifies an organization at most, and organization_id is already on the span.
            "client.name",
            // The same facts under their OpenTelemetry semantic-convention names. Nothing in this build emits them
            // today - the spans come from Micrometer observations, not from the OTel agent - and they are kept
            // deliberately, unlike the sweep.* keys an earlier draft reserved. Those were names invented for this
            // plan that nothing would ever set; these are names fixed by a published specification that a framework
            // upgrade would start emitting. Spring's own OpenTelemetryServerRequestObservationConvention ships in
            // this spring-web and is a plausible future default: its low-cardinality keys are all here, and its
            // high-cardinality url.path and http.request.method_original are deliberately absent, so that switch
            // keeps telemetry intact and opens no leak. Every name below is structurally non-personal.
            "http.request.method", "http.response.status_code", "http.route", "server.address", "network.protocol.name",
            "url.scheme", "error.type",
            // Database, and on the same footing as the semantic-convention block above rather than a description of
            // what leaves: nothing in this build emits them today. No JDBC observation instrumentation is on the
            // classpath, so these are a forward reservation against the day one is added. db.query.text is
            // deliberately absent whenever that day comes, because a parameter can carry anything.
            "db.system.name", "db.operation.name", "db.collection.name",
            // Spring Security's own observations, restricted to the closed-set, low-cardinality ones:
            // "request"/"method" for the object, "true"/"false" for the decision, a filter's simple class name and
            // "before"/"after" for the section, and an Authentication's simple class name for the type.
            // spring.security.authentication.authorities is deliberately absent: it renders the granted authorities,
            // which are per-user, unbounded, and in this product derive from directory groups, so it can carry a
            // customer's internal group names. spring.security.authorization.decision.details is absent too: it is
            // String.valueOf over an object whose toString nobody here controls.
            "spring.security.object", "spring.security.authorization.decision", "spring.security.reached.filter.name",
            "spring.security.reached.filter.section", "spring.security.authentication.type");

    private AttributeAllowlist() {
    }
}
