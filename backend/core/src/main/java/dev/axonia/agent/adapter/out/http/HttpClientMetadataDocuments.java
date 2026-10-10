package dev.axonia.agent.adapter.out.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import dev.axonia.agent.application.ClientMetadata;
import dev.axonia.agent.application.port.out.ClientMetadataDocuments;
import dev.axonia.agent.configuration.McpOAuthProperties;
import dev.axonia.shared.api.time.ClockTicker;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches a Client ID Metadata Document from an allowlisted host only, so a person is only ever asked to trust a client
 * whose identity an operator already vouched for. The allowlist is checked before the cache, so dropping a host still
 * takes effect at once.
 *
 * <p>
 * A valid document is kept for the lifetime its response asked for, bounded by {@link ClientMetadataLifetime}, and is
 * never served past it. A failure or an invalid document is never kept (draft-ietf-oauth-client-id-metadata-document-02
 * section 5.2): the next request fetches again, and a refetch that fails refuses the request (section 5.1). Concurrent
 * resolutions of one client share one fetch, its failure included: the first caller fetches on its own thread and the
 * others wait for its outcome, so no lock is held while the document is fetched.
 */
@Slf4j
@Component
class HttpClientMetadataDocuments implements ClientMetadataDocuments {

    /** Section 8.7 recommends reading at most 5 kilobytes. */
    private static final int MAX_BODY_BYTES = 5 * 1024;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_ENTRIES = 256;

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final McpOAuthProperties properties;
    private final AsyncCache<String, Resolved> documents;

    HttpClientMetadataDocuments(@Qualifier("clientMetadataRestClient") RestClient restClient, JsonMapper jsonMapper,
            McpOAuthProperties properties, Clock clock) {

        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.documents = Caffeine.newBuilder().ticker(new ClockTicker(clock)).executor(Runnable::run)
                .maximumSize(MAX_ENTRIES)
                .expireAfter(Expiry.<String, Resolved>creating((clientId, resolved) -> resolved.lifetime()))
                .buildAsync();
    }

    /**
     * The caller whose future the cache stored completes it, in a {@code finally} so that no outcome, an error
     * included, can leave the others waiting. A null outcome is removed from the cache as it completes.
     */
    @Override
    public Optional<ClientMetadata> resolve(String clientId) {

        var url = trustedUrl(clientId);
        if (url == null) {
            return Optional.empty();
        }
        var fetch = new CompletableFuture<Resolved>();
        var shared = documents.get(clientId, (key, executor) -> fetch);
        if (shared == fetch) {
            try {
                fetch.complete(load(clientId, url));
            } finally {
                fetch.complete(null);
            }
        }
        return Optional.ofNullable(shared.join()).map(Resolved::metadata);
    }

    private URI trustedUrl(String clientId) {

        if (clientId == null) {
            return null;
        }
        try {
            var uri = new URI(clientId);
            var path = uri.getRawPath();
            var trusted = "https".equals(uri.getScheme()) && properties.trustsHostOf(clientId) && uri.getPort() == -1
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && path != null && path.length() > 1;
            return trusted ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Null for every non-answer, so nothing is stored for it. */
    private Resolved load(String clientId, URI url) {

        return fetch(url).flatMap(fetched -> read(clientId, url, fetched.body())
                .map(metadata -> new Resolved(metadata, fetched.lifetime()))).orElse(null);
    }

    /** Section 5: only a 200 is a document; every other status is an error response. */
    private Optional<Fetched> fetch(URI url) {

        try {
            return restClient.get().uri(url).accept(MediaType.APPLICATION_JSON).exchange((request, response) -> {
                if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)
                        || !isJson(response.getHeaders().getContentType())) {
                    return Optional.<Fetched>empty();
                }
                var body = response.getBody().readNBytes(MAX_BODY_BYTES + 1);
                if (body.length > MAX_BODY_BYTES) {
                    return Optional.<Fetched>empty();
                }
                return Optional
                        .of(new Fetched(body, ClientMetadataLifetime.of(response.getHeaders().getCacheControl())));
            });
        } catch (RuntimeException e) {
            log.warn("Client metadata fetch failed for {}: {}", url, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<ClientMetadata> read(String clientId, URI url, byte[] body) {

        try {
            var document = jsonMapper.readTree(body);
            var name = document.path("client_name").asString("");
            var authMethod = document.path("token_endpoint_auth_method").asString("none");
            var redirectUris = strings(document.path("redirect_uris"));
            var valid = clientId.equals(document.path("client_id").asString("")) && !name.isBlank()
                    && name.length() <= MAX_NAME_LENGTH && "none".equals(authMethod) && allowsCodeGrant(document)
                    && !redirectUris.isEmpty()
                    && redirectUris.stream().allMatch(HttpClientMetadataDocuments::isLoopback);
            return valid
                    ? Optional.of(new ClientMetadata(clientId, name.strip(), url.getHost().toLowerCase(Locale.ROOT),
                            redirectUris))
                    : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isJson(MediaType type) {

        return type != null && "application".equals(type.getType())
                && ("json".equals(type.getSubtype()) || "json".equals(type.getSubtypeSuffix()));
    }

    /** RFC 7591 section 2: an absent {@code grant_types} means {@code authorization_code}. */
    private static boolean allowsCodeGrant(JsonNode document) {

        var grants = document.path("grant_types");
        return grants.isMissingNode() || strings(grants).contains("authorization_code");
    }

    private static List<String> strings(JsonNode array) {

        var values = new ArrayList<String>();
        if (array.isArray()) {
            array.forEach(node -> values.add(node.isString() ? node.asString() : ""));
        }
        return values;
    }

    private static boolean isLoopback(String value) {

        try {
            return ClientMetadata.isLoopback(new URI(value));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private record Fetched(byte[] body, Duration lifetime) {
    }

    private record Resolved(ClientMetadata metadata, Duration lifetime) {
    }
}
