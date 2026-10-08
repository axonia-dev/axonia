package dev.axonia.selfhosted;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** What {@code selfhosted-edge} adds to every request it proxies, for MockMvc requests to the gated backend. */
public final class SelfHostedRequests {

    public static final String EDGE_HEADER = "X-Axonia-Edge-Auth";

    private SelfHostedRequests() {
    }

    /** The edge's shared secret, and nothing else: an anonymous request through the edge. */
    public static RequestPostProcessor throughTheEdge() {

        return request -> {
            request.addHeader(EDGE_HEADER, SelfHostedTestInfrastructure.EDGE_SECRET);
            return request;
        };
    }

    /** The edge's shared secret and {@code token} as the bearer, as the edge sends a signed-in browser's request. */
    public static RequestPostProcessor signedIn(String token) {

        return request -> {
            request.addHeader(EDGE_HEADER, SelfHostedTestInfrastructure.EDGE_SECRET);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
