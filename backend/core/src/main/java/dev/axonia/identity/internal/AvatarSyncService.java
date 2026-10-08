package dev.axonia.identity.internal;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import dev.axonia.TokenHasher;
import dev.axonia.identity.OrganizationIdpResolver;
import dev.axonia.media.MediaKeys;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciles a member's avatar with whatever their identity provider offers, so the bytes always end up in our own
 * storage and the member's browser never fetches an image from a host we do not control.
 *
 * <p>
 * Two sources, one pipeline. Graph is preferred when the Organization federates Entra and the member carries an
 * {@code external_id}, because it exposes an {@code @odata.mediaEtag} and an unchanged photo therefore costs one small
 * JSON call. Otherwise the {@code picture} claim URL supplied by the caller is fetched, subject to
 * {@link AvatarHostPolicy} and the constraints in {@link ClaimUrlPhotoSource}.
 *
 * <p>
 * The claim URL is a parameter rather than a column. It is never persisted: the address served is minted from
 * {@code avatar_media_key}, always our own storage or nothing. Persisting the claim URL would put the sign-in upsert
 * and this sync in a permanent fight over the same column.
 *
 * <p>
 * The two sources detect change differently, and that asymmetry is deliberate. Graph has a server etag. The claim path
 * has none, and a stable URL is no substitute - GitHub's avatar URL does not change when the image behind it does - so
 * it refetches on the staleness window and compares a hash of the bytes. Both values live in {@code avatar_etag}.
 *
 * <p>
 * Reliability is reconciliation, not a durable queue: {@code avatar_synced_at} is stamped only on success, so any lost
 * or failed task is re-derived by the next provision after the staleness window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarSyncService {

    static final Duration STALE_AFTER = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final EntraPhotoSource photoSource;
    private final ClaimUrlPhotoSource claimUrlPhotoSource;
    private final MediaStorage mediaStorage;
    private final OrganizationIdpResolver resolver;
    private final AvatarSyncExecutor avatarSyncExecutor;
    private final Clock clock;
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    /** Cheap gates inline; the fetch and storage work on the executor. Never throws, never blocks on I/O. */
    public void maybeSync(String organizationId, String userId, String claimAvatarUrl) {

        var user = userRepository.findByOrganizationIdAndUserId(organizationId, userId).orElse(null);
        if (user == null || fresh(user.getAvatarSyncedAt())) {
            return;
        }
        var tenant = entraTenantFor(organizationId, user);
        if (tenant == null && (claimAvatarUrl == null || claimAvatarUrl.isBlank())) {
            return;
        }
        submit(user, tenant, claimAvatarUrl);
    }

    /**
     * The Entra tenant to read this member's photo from, or null when Graph is not this member's source.
     *
     * <p>
     * A tenant the Organization has not proved it owns is not a source either, and the member falls back to their
     * {@code picture} claim: {@code aadTenantId} is typed by an administrator and axonia's app registration is
     * multi-tenant, so reading photos out of it before {@link dev.axonia.identity.OrganizationIdp#graphReadsAllowed()}
     * says the tenant is proved would be reading a directory nobody has shown this Organization any claim on.
     */
    private String entraTenantFor(String organizationId, UserEntity user) {

        if (user.getExternalId() == null) {
            return null;
        }
        var idp = resolver.resolve(organizationId).orElse(null);
        return idp != null && idp.provider().federatesMicrosoftDirectory() && idp.graphReadsAllowed()
                ? idp.aadTenantId()
                : null;
    }

    private boolean fresh(Instant syncedAt) {

        return syncedAt != null && syncedAt.isAfter(clock.instant().minus(STALE_AFTER));
    }

    private void submit(UserEntity user, String aadTenantId, String claimAvatarUrl) {

        var key = user.getOrganizationId() + "|" + user.getUserId();
        if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        var accepted = avatarSyncExecutor.trySubmit(() -> {
            try {
                sync(user, aadTenantId, claimAvatarUrl);
            } finally {
                inFlight.remove(key);
            }
        });
        if (!accepted) {
            inFlight.remove(key);
            log.warn("Avatar sync queue full - skipped {} (a later window retries)", key);
        }
    }

    private void sync(UserEntity user, String aadTenantId, String claimAvatarUrl) {

        try {
            if (aadTenantId != null) {
                reconcileFromGraph(user, aadTenantId);
            } else {
                reconcileFromClaim(user, claimAvatarUrl);
            }
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn(
                    "Graph refused the photo read for {}|{} - check the ProfilePhoto.Read.All application "
                            + "permission and admin consent on the axonia.idp.entra.* app registration",
                    user.getOrganizationId(), user.getUserId(), e);
        } catch (RuntimeException e) {
            log.warn("Avatar sync failed for {}|{} - avatar_synced_at left unstamped, a later window retries",
                    user.getOrganizationId(), user.getUserId(), e);
        }
    }

    private void reconcileFromGraph(UserEntity user, String aadTenantId) {

        var metadata = photoSource.photoMetadata(user.getExternalId(), aadTenantId);
        if (metadata.isEmpty()) {
            handleNoPhoto(user);
            return;
        }
        if (Objects.equals(metadata.get().etag(), user.getAvatarEtag()) && user.getAvatarEtag() != null) {
            userRepository.touchAvatarSyncedAt(user.getOrganizationId(), user.getUserId(), clock.instant());
            return;
        }
        store(user, photoSource.fetchPhoto(user.getExternalId(), aadTenantId), metadata.get().etag());
    }

    private void reconcileFromClaim(UserEntity user, String claimAvatarUrl) {

        var photo = claimUrlPhotoSource.fetch(claimAvatarUrl);
        if (photo.isEmpty()) {
            handleNoPhoto(user);
            return;
        }
        var hash = contentHash(photo.get().bytes());
        if (hash.equals(user.getAvatarEtag()) && user.getAvatarMediaKey() != null) {
            userRepository.touchAvatarSyncedAt(user.getOrganizationId(), user.getUserId(), clock.instant());
            return;
        }
        store(user, photo, hash);
    }

    /** The bytes' identity, standing in for a server etag on a source that offers none. */
    static String contentHash(byte[] bytes) {

        return TokenHasher.sha256Hex(bytes);
    }

    private void handleNoPhoto(UserEntity user) {

        if (user.getAvatarMediaKey() != null) {
            userRepository.clearSyncedAvatar(user.getOrganizationId(), user.getUserId(), clock.instant());
            deleteQuietly(user.getAvatarMediaKey());
        } else {
            userRepository.touchAvatarSyncedAt(user.getOrganizationId(), user.getUserId(), clock.instant());
        }
    }

    private void store(UserEntity user, Optional<AvatarPhoto> photo, String etag) {

        if (photo.isEmpty()) {
            // The photo vanished between the metadata read and the bytes read.
            handleNoPhoto(user);
            return;
        }
        var bytes = photo.get().bytes();
        var key = MediaKeys.avatar(user.getOrganizationId(), photo.get().contentType());
        mediaStorage.store(key, new ByteArrayInputStream(bytes), photo.get().contentType(), bytes.length);
        userRepository.updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId(user.getOrganizationId())
                .userId(user.getUserId()).avatarMediaKey(key).avatarEtag(etag).avatarSyncedAt(clock.instant()).build());
        deleteQuietly(user.getAvatarMediaKey());
    }

    private void deleteQuietly(String key) {

        if (key == null) {
            return;
        }
        try {
            mediaStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Orphaned media blob '{}' - storage delete failed after avatar refresh", key, e);
        }
    }
}
