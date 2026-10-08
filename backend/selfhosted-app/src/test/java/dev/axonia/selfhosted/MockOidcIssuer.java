package dev.axonia.selfhosted;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import tools.jackson.databind.json.JsonMapper;

/**
 * A real OpenID Connect issuer for the Self-hosted tests: {@code mock-oauth2-server} in a container, once per test JVM.
 * Its tokens are RS256-signed with keys the backend fetches through discovery, exactly as from a company's own identity
 * provider.
 *
 * <p>
 * A token is minted through the server's JWT bearer grant: the assertion carries the claims, and the server copies
 * every one of them into the token it signs. It sets {@code iss}, {@code iat}, {@code nbf}, {@code exp}, {@code jti}
 * and {@code tid} itself, and takes {@code aud} from the request's scope rather than from the assertion, which is why
 * an {@code aud} in {@code claims} travels as the scope.
 */
public final class MockOidcIssuer {

    public static final String IMAGE = "ghcr.io/navikt/mock-oauth2-server:6.0.4";

    public static final String ISSUER_ID = "selfhosted";

    public static final String AUDIENCE = "axonia-selfhosted";

    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private static final int PORT = 8080;

    private static final MockOidcIssuer SHARED = new MockOidcIssuer();

    private final GenericContainer<?> container = new GenericContainer<>(IMAGE).withExposedPorts(PORT).waitingFor(
            Wait.forHttp("/isalive").forPort(PORT).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));

    private final HttpClient http = HttpClient.newHttpClient();

    private final JsonMapper json = JsonMapper.builder().build();

    private MockOidcIssuer() {

        container.start();
    }

    public static MockOidcIssuer shared() {

        return SHARED;
    }

    /** The issuer as the backend sees it and as every token from {@link #token} names it. */
    public String issuerUri() {

        return issuerUri(ISSUER_ID);
    }

    /**
     * A signed access token with {@code sub} = {@code subject} (no {@code sub} at all when {@code subject} is null) and
     * every entry of {@code claims}. Its audience is {@link #AUDIENCE} unless {@code claims} holds {@code "aud"}: a
     * string, or a collection of strings, of which an empty one gives a token with no audience.
     */
    public String token(String subject, Map<String, Object> claims) {

        return mint(ISSUER_ID, subject, claims);
    }

    /** The same, signed by another issuer of the same server: another {@code iss} and another key. */
    public String foreignToken(String subject, Map<String, Object> claims) {

        return mint("elsewhere", subject, claims);
    }

    private String issuerUri(String issuerId) {

        return "http://" + container.getHost() + ":" + container.getMappedPort(PORT) + "/" + issuerId;
    }

    private String mint(String issuerId, String subject, Map<String, Object> claims) {

        var assertion = new HashMap<>(claims);
        var audience = assertion.remove("aud");
        if (subject != null) {
            assertion.put("sub", subject);
        }
        var form = Map.of("grant_type", JWT_BEARER, "assertion", signed(assertion), "scope", scope(audience),
                "client_id", "axonia-tests", "client_secret", "unused");
        var request = HttpRequest.newBuilder(URI.create(issuerUri(issuerId) + "/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(form))).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("mock-oauth2-server refused the token request: " + response.body());
            }
            return json.readTree(response.body()).get("access_token").asString();
        } catch (IOException e) {
            throw new IllegalStateException("mock-oauth2-server did not answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while minting a token", e);
        }
    }

    private static String scope(Object audience) {

        if (audience == null) {
            return AUDIENCE;
        }
        if (audience instanceof Collection<?> audiences) {
            return audiences.isEmpty()
                    ? "openid"
                    : audiences.stream().map(String::valueOf).collect(Collectors.joining(" "));
        }
        return String.valueOf(audience);
    }

    private static String signed(Map<String, Object> claims) {

        try {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.parse(claims));
            jwt.sign(new MACSigner(new byte[32]));
            return jwt.serialize();
        } catch (JOSEException | ParseException e) {
            throw new IllegalStateException("could not build the assertion", e);
        }
    }

    private static String formBody(Map<String, String> form) {

        return form.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
