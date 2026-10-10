package dev.axonia.selfhosted;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * An OpenID provider on a loopback port: a discovery document, and a key set whose endpoint can be made to fail the
 * ways a real one does. It counts every request to each, and keeps the headers of every request.
 */
public final class LocalIssuer implements AutoCloseable {

    public static final String AUDIENCE = "api://axonia";

    /** What the key endpoint does with a request; {@code HANGING} never answers before the issuer closes. */
    public enum Keys {
        SERVED, SLOW, TRICKLING, HANGING, FAILING, THROTTLED, MALFORMED, MISSING, GONE
    }

    public final RSAKey first = key("first");

    public final AtomicInteger discoveryFetches = new AtomicInteger();

    public final AtomicInteger keyFetches = new AtomicInteger();

    /** Requests to {@code /moved-keys}, which always serves the keys, for a discovery document that names it. */
    public final AtomicInteger movedKeyFetches = new AtomicInteger();

    public final Queue<Headers> requestHeaders = new ConcurrentLinkedQueue<>();

    public volatile Keys keys = Keys.SERVED;

    private final HttpServer server;

    private final ExecutorService handlers = Executors.newCachedThreadPool();

    private volatile List<JWK> published = List.of(first);

    private volatile String discovery;

    private LocalIssuer(HttpServer server) {

        this.server = server;
        discovery = "{\"issuer\":\"" + uri() + "\",\"jwks_uri\":\"" + uri() + "/keys\"}";
    }

    public static LocalIssuer start() {

        try {
            var issuer = new LocalIssuer(
                    HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
            issuer.server.setExecutor(issuer.handlers);
            issuer.server.createContext("/.well-known/openid-configuration", issuer::discovery);
            issuer.server.createContext("/keys", issuer::keys);
            issuer.server.createContext("/moved-keys", issuer::movedKeys);
            issuer.server.start();
            return issuer;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String uri() {

        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Publishes a new key beside the others, as an identity provider does before it signs with it. */
    public RSAKey rotate() {

        return publish(key("rotated"));
    }

    public <K extends JWK> K publish(K key) {

        published = Stream.concat(published.stream(), Stream.of(key)).toList();
        return key;
    }

    public void discovers(String document) {

        discovery = document;
    }

    public String token(RSAKey key) {

        return token(uri(), key);
    }

    /** A token for this issuer signed with a key it never published. */
    public String forged() {

        return token(key(UUID.randomUUID().toString()));
    }

    /** A token for this issuer whose header names the algorithm and key, signed by the signer whatever they say. */
    public String token(JWSAlgorithm algorithm, String keyId, JWSSigner signer) {

        return token(uri(), algorithm, keyId, signer);
    }

    /** A token for this issuer that carries no signature. */
    public String unsigned() {

        return new PlainJWT(claims(uri())).serialize();
    }

    public static String token(String issuer, RSAKey key) {

        try {
            return token(issuer, JWSAlgorithm.RS256, key.getKeyID(), new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(String issuer, JWSAlgorithm algorithm, String keyId, JWSSigner signer) {

        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(keyId).type(JOSEObjectType.JWT).build(),
                claims(issuer));
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static JWTClaimsSet claims(String issuer) {

        var now = Instant.now();
        return new JWTClaimsSet.Builder().issuer(issuer).audience(AUDIENCE).subject("alice").issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600))).build();
    }

    public static RSAKey key(String id) {

        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ECKey ecKey(String id, Curve curve) {

        try {
            return new ECKeyGenerator(curve).keyID(id).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private void discovery(HttpExchange exchange) throws IOException {

        discoveryFetches.incrementAndGet();
        requestHeaders.add(exchange.getRequestHeaders());
        respond(exchange, 200, discovery);
    }

    private void movedKeys(HttpExchange exchange) throws IOException {

        movedKeyFetches.incrementAndGet();
        respond(exchange, 200, publishedKeys());
    }

    private void keys(HttpExchange exchange) throws IOException {

        keyFetches.incrementAndGet();
        requestHeaders.add(exchange.getRequestHeaders());
        switch (keys) {
            case FAILING -> respond(exchange, 503, "");
            case THROTTLED -> respond(exchange, 429, "");
            case MISSING -> respond(exchange, 404, "");
            case GONE -> respond(exchange, 410, "");
            case MALFORMED -> respond(exchange, 200, "<html>Service temporarily replaced</html>");
            case SLOW -> {
                pause(Duration.ofSeconds(1));
                respond(exchange, 200, publishedKeys());
            }
            case TRICKLING -> trickle(exchange, publishedKeys());
            case HANGING -> pause(Duration.ofMinutes(1));
            case SERVED -> respond(exchange, 200, publishedKeys());
        }
    }

    private String publishedKeys() {

        return new JWKSet(published.stream().map(JWK::toPublicJWK).toList()).toString();
    }

    /** Answers a byte a second for seven seconds before the set, so no single read waits long enough to time out. */
    private static void trickle(HttpExchange exchange, String json) throws IOException {

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        try (var out = exchange.getResponseBody()) {
            for (var second = 0; second < 7; second++) {
                out.write(' ');
                out.flush();
                pause(Duration.ofSeconds(1));
            }
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void pause(Duration duration) {

        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {

        var body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {

        server.stop(0);
        handlers.shutdownNow();
    }
}
