package dev.axonia.shared.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeGateFilterTest {

    private static final String SECRET = "the-current-secret";

    private final EdgeGateFilter sut = new EdgeGateFilter(List.of(SECRET, "the-previous-secret"));

    @Test
    void letsThroughARequestCarryingTheCurrentSecret() throws Exception {

        var request = new MockHttpServletRequest("GET", "/axonia/communities");
        request.addHeader(EdgeGateFilter.HEADER, SECRET);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsThroughARequestCarryingThePreviousSecretSoRotationHasAWindow() throws Exception {

        var request = new MockHttpServletRequest("GET", "/axonia/communities");
        request.addHeader(EdgeGateFilter.HEADER, "the-previous-secret");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void refusesARequestWithNoSecretAtAll() throws Exception {

        var request = new MockHttpServletRequest("GET", "/axonia/communities");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void refusesARequestWithAWrongSecret() throws Exception {

        var request = new MockHttpServletRequest("GET", "/axonia/communities");
        request.addHeader(EdgeGateFilter.HEADER, "a-guess");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void neverEchoesSecretMaterialInTheRefusal() throws Exception {

        var request = new MockHttpServletRequest("GET", "/axonia/communities");
        request.addHeader(EdgeGateFilter.HEADER, "a-guess");
        var response = new MockHttpServletResponse();

        sut.doFilter(request, response, new MockFilterChain());

        assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("a-guess")
                .doesNotContain("the-previous-secret");
    }

    @Test
    void letsThePlatformHealthProbeThroughUngated() throws Exception {

        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsTheLivenessProbeThroughToo() throws Exception {

        var request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsTheReadinessProbeThroughToo() throws Exception {

        var request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void refusesAPathThatOnlyLooksLikeTheHealthProbe() throws Exception {

        var request = new MockHttpServletRequest("GET", "/actuator/healthz");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void refusesATraversalThatHidesAGatedPathBehindTheHealthPrefix() throws Exception {

        var request = new MockHttpServletRequest("GET", "/actuator/health/../../axonia/communities");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void refusesAnEncodedTraversalBehindTheHealthPrefix() throws Exception {

        var request = new MockHttpServletRequest("GET", "/actuator/health/%2e%2e/%2e%2e/axonia/communities");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        sut.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }
}
