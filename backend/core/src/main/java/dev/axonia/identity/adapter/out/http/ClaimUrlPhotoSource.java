package dev.axonia.identity.adapter.out.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.axonia.identity.application.AvatarFetchFailedException;
import dev.axonia.identity.application.AvatarHostPolicy;
import dev.axonia.identity.application.AvatarSyncService;
import dev.axonia.identity.application.port.out.ClaimPhotoSource;
import dev.axonia.identity.configuration.AvatarProperties;
import dev.axonia.identity.domain.AvatarPhoto;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import dev.axonia.shared.api.metrics.IntegrationMetrics.Integration;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches an identity provider's {@code picture} URL server-side so the bytes can be mirrored into our own storage and
 * the member's browser never talks to the provider.
 *
 * <p>
 * Every constraint here exists because the URL is attacker-influenceable in principle: it arrives on a token claim. The
 * host allowlist is applied before the first byte, the destination of a redirect is re-checked against the same
 * allowlist and only one hop is allowed, resolved addresses outside the public ranges are refused, the content type
 * must be an image we know, and the body is truncated at a hard ceiling read from the stream rather than trusted from
 * {@code Content-Length}.
 *
 * <p>
 * <strong>Known limit:</strong> the address check and the connection are two separate acts, so a host that resolves
 * differently between them is not caught. Against an allowlist of hosts we federate with, this is belt-and-braces
 * rather than the load-bearing control.
 *
 * <p>
 * {@link #fetch(String)} does not always return normally. It answers {@link Optional#empty()} whenever emptiness is
 * itself the finding: policy refusal, a null, blank or malformed URL, a host that does not resolve at all, a non-public
 * resolved address, a 4xx other than 429, a redirect the policy refuses, a malformed or repeated redirect target, a
 * content type that is not a recognised image, or a body that is empty or over the size ceiling. It throws
 * {@link AvatarFetchFailedException} when the attempt says nothing about the photo and a retry might succeed instead:
 * any other {@link IOException} while sending or reading, an interrupted request, or a 429/5xx response.
 * {@link AvatarSyncService} relies on that split to leave {@code avatar_synced_at} unstamped on a transient failure
 * rather than clearing a good avatar over one bad network blip. That policy lives in exactly one place - the catch
 * clauses of {@link #fetch(String)} itself - every helper below just propagates the checked exceptions it gets.
 *
 * <p>
 * Every request that actually leaves is counted against the {@code avatar} integration, including the second one when a
 * redirect is followed - this is the one outbound client in the application that is not a {@code RestClient}, so
 * nothing else would have counted it. A URL refused before any byte left, by the allowlist or by the address check, is
 * not counted: no call was made.
 *
 * <p>
 * What counts as a failed call is deliberately not the same split as what {@link #fetch(String)} answers. A 404 or a
 * 410 is the photo being gone, which is a finding about the photo, so it counts as a success - the same judgement
 * {@code S3MediaStorage.open} makes about a missing key, and the same one {@code EntraPhotoSource} declares to the
 * interceptor for Graph's photo 404. Every other 4xx is the host refusing this application, and 429 and 5xx are the two
 * shapes this class already treats as retryable; all of those count as failures. The distinction is load-bearing rather
 * than tidy: a host that started answering 403 to everything would make every fetch answer empty, and
 * {@code AvatarSyncService} answers empty by clearing the stored avatar, so the estate's claim-sourced avatars would be
 * wiped one tick at a time. None of it is on the request path, so no user sees an error and no other meter moves. A
 * counter reading 100 percent success through that would be worse than no counter at all.
 *
 * <p>
 * <strong>Never on the request path</strong> - callers run on the avatar-sync executor.
 */
@Slf4j
@Component
public class ClaimUrlPhotoSource implements ClaimPhotoSource {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    /** The two replies that mean the photo is not there, which is a finding about the photo rather than a refusal. */
    private static final Set<Integer> PHOTO_IS_GONE = Set.of(404, 410);

    private final AvatarHostPolicy policy;
    private final AvatarProperties properties;
    private final IntegrationMetrics metrics;
    private final boolean rejectNonPublicAddresses;
    private final HttpClient client;

    @Autowired
    ClaimUrlPhotoSource(AvatarHostPolicy policy, AvatarProperties properties, IntegrationMetrics metrics) {
        this(policy, properties, metrics, true);
    }

    ClaimUrlPhotoSource(AvatarHostPolicy policy, AvatarProperties properties, IntegrationMetrics metrics,
            boolean rejectNonPublicAddresses) {
        this.policy = policy;
        this.properties = properties;
        this.metrics = metrics;
        this.rejectNonPublicAddresses = rejectNonPublicAddresses;
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.timeout()).build();
    }

    /** The photo behind a claim URL. See the class javadoc for what returns empty versus what throws. */
    @Override
    public Optional<AvatarPhoto> fetch(String url) {

        try {
            return attempt(url, true);
        } catch (UnknownHostException | IllegalArgumentException e) {
            log.debug("Avatar fetch refused a claim URL", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AvatarFetchFailedException("Interrupted while fetching an avatar", e);
        } catch (IOException e) {
            throw new AvatarFetchFailedException("Transport failure while fetching an avatar", e);
        }
    }

    private Optional<AvatarPhoto> attempt(String url, boolean mayRedirect) throws IOException, InterruptedException {

        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        var uri = URI.create(url);
        if (!permitted(uri)) {
            return Optional.empty();
        }
        return handle(send(uri), uri, mayRedirect);
    }

    /**
     * The one act that leaves this process, and so the one thing counted. The 429/5xx refusal is raised here rather
     * than in {@link #handle} so that the counted region covers the judgement about the response as well as the
     * request: the exception is identical to the one that used to be raised a frame later, and {@link #fetch}'s catch
     * clauses are unchanged. The redirect hop is deliberately outside this region, so following one is a second counted
     * call of its own rather than a nested timing inside the first.
     */
    private HttpResponse<InputStream> send(URI uri) throws IOException, InterruptedException {

        return metrics.callChecked(Integration.AVATAR, () -> {
            var response = client.send(HttpRequest.newBuilder(uri).timeout(properties.timeout()).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new AvatarFetchFailedException(
                        "Avatar fetch got HTTP " + response.statusCode() + " for a claim URL", null);
            }
            return response;
        }, response -> refusal(response.statusCode()));
    }

    /**
     * The status, when the host refused, and null when it answered. The response is handed back either way, so
     * {@link #handle} still turns everything but a 200 or a redirect into {@link Optional#empty()}: this changes what
     * the meter records, never what the caller receives.
     */
    private static String refusal(int status) {

        return status >= 400 && !PHOTO_IS_GONE.contains(status) ? "HTTP " + status : null;
    }

    private boolean permitted(URI uri) throws UnknownHostException {
        return policy.permits(uri) && addressIsPublic(uri);
    }

    private Optional<AvatarPhoto> handle(HttpResponse<InputStream> response, URI uri, boolean mayRedirect)
            throws IOException, InterruptedException {

        var status = response.statusCode();
        if (isRedirect(status)) {
            return followRedirect(response, uri, mayRedirect);
        }
        if (status == 200) {
            return body(response);
        }
        return Optional.empty();
    }

    private Optional<AvatarPhoto> followRedirect(HttpResponse<InputStream> response, URI uri, boolean mayRedirect)
            throws IOException, InterruptedException {

        var location = response.headers().firstValue("Location").orElse(null);
        if (!mayRedirect || location == null) {
            return Optional.empty();
        }
        return attempt(uri.resolve(location).toString(), false);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private Optional<AvatarPhoto> body(HttpResponse<InputStream> response) throws IOException {

        var contentType = response.headers().firstValue("Content-Type").orElse("").split(";")[0].trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType)) {
            return Optional.empty();
        }
        try (var stream = response.body()) {
            var bytes = stream.readNBytes(properties.maxBytes() + 1);
            if (bytes.length == 0 || bytes.length > properties.maxBytes()) {
                return Optional.empty();
            }
            return Optional.of(new AvatarPhoto(bytes, contentType));
        }
    }

    private boolean addressIsPublic(URI uri) throws UnknownHostException {

        if (!rejectNonPublicAddresses) {
            return true;
        }
        for (var address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                return false;
            }
        }
        return true;
    }
}
