package dev.axonia.identity.internal;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import dev.axonia.IntegrationMetrics;

/**
 * Microsoft Graph photo reads for avatar sync. Metadata first (cheap JSON with {@code @odata.mediaEtag}), bytes only
 * when the caller decides the photo changed - so an unchanged photo costs one small JSON call, never a download.
 *
 * <p>
 * <strong>Deployment prerequisite:</strong> the {@code axonia.idp.entra.*} app registration must be multi-tenant and
 * hold the {@code ProfilePhoto.Read.All} application permission (not delegated), with admin consent granted in each
 * customer tenant.
 *
 * <p>
 * Every read is made against the {@code aadTenantId} of the user's own Organization, supplied by the caller, so one
 * customer's avatar sync can never reach into another customer's directory. The token for that tenant is the Graph
 * client's to mint, keep and renew.
 *
 * <p>
 * <strong>Never on the request path</strong> - callers run on the avatar-sync executor.
 */
@Component
class EntraPhotoSource {

    static final String PHOTO_SIZE = "240x240";

    /** Photo descriptor without the bytes; the etag decides whether a download is needed. */
    record PhotoMetadata(String etag) {
    }

    private final RestClient graphClient;

    EntraPhotoSource(@Qualifier("entraGraphRestClient") RestClient graphClient) {
        this.graphClient = graphClient;
    }

    /**
     * Photo metadata for a directory user; empty when the user has no photo (Graph 404).
     *
     * <p>
     * That 404 is the ordinary case, not an incident - most members of a large directory never set a photo - and this
     * runs once per member per sync. {@link IntegrationMetrics#EXPECTED_STATUS} says so at the call, so the outbound
     * counter records a success and logs nothing rather than putting a floor under {@code entra}'s failure rate and a
     * WARN in the log for every member without a photo. The catch below is what turns it into the empty answer; the
     * attribute only tells the meter. Both are needed: the interceptor runs below {@code retrieve()} and never sees
     * what the catch does.
     */
    Optional<PhotoMetadata> photoMetadata(String externalId, String aadTenantId) {

        try {
            var body = graphClient.get().uri("/v1.0/users/{id}/photo", externalId)
                    .attribute(IntegrationMetrics.EXPECTED_STATUS, HttpStatus.NOT_FOUND)
                    .attributes(EntraGraphRegistrations.actingFor(aadTenantId)).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            var etag = body != null ? (String) body.get("@odata.mediaEtag") : null;
            return Optional.of(new PhotoMetadata(etag));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /**
     * The {@value #PHOTO_SIZE} photo bytes; empty when Graph has no photo (404) or answers without a body. Carries the
     * same expected-status marker as {@link #photoMetadata}, on the same grounds.
     */
    Optional<AvatarPhoto> fetchPhoto(String externalId, String aadTenantId) {

        try {
            var response = graphClient.get().uri("/v1.0/users/{id}/photos/" + PHOTO_SIZE + "/$value", externalId)
                    .attribute(IntegrationMetrics.EXPECTED_STATUS, HttpStatus.NOT_FOUND)
                    .attributes(EntraGraphRegistrations.actingFor(aadTenantId)).retrieve().toEntity(byte[].class);
            var bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                return Optional.empty();
            }
            var contentType = response.getHeaders().getContentType();
            return Optional.of(new AvatarPhoto(bytes, contentType != null ? contentType.toString() : "image/jpeg"));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
