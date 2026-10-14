package dev.axonia.identity.application;

import java.io.ByteArrayInputStream;
import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.media.api.ImageLimits;
import dev.axonia.media.api.ImageUpload;
import dev.axonia.media.api.ImageValidator;
import dev.axonia.media.api.MediaKeys;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A member's own picture: uploaded by them, stored by axonia, and served ahead of whatever the mirror holds. The mirror
 * is deliberately untouched here, so clearing an override lands on a current photo.
 *
 * <p>
 * {@link #set(ImageUpload)} and {@link #clear()} return the key of the blob their write superseded, if any, rather than
 * deleting it themselves. Object storage has no rollback and is not part of the database transaction, so a delete
 * issued before commit outlives a commit that then fails: the row keeps pointing at a blob that is already gone, a
 * permanently broken avatar URL, which is strictly worse than the orphaned blob this ordering tolerates instead. The
 * caller (see {@code UserController}) passes the returned key to {@link #deletePreviousBlob(String)} only after the
 * write call has returned - by then the transactional method's commit has already happened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarOverrideService {

    /**
     * The same ceiling the BADGE asset and axonia.avatar.max-bytes already apply, so an override can never be heavier
     * than a synced photo, and the same 512 pixel edge as the BADGE.
     */
    private static final ImageLimits LIMITS = new ImageLimits(2L * 1024 * 1024, 512);

    private final UserStore userStore;
    private final ClaimsReader claimsReader;
    private final MediaStorage mediaStorage;
    private final Clock clock;

    @Transactional
    public String set(ImageUpload upload) {

        ImageValidator.validate(upload, LIMITS);
        var claims = claimsReader.read();
        var user = requireProfile(claims.organizationId(), claims.userId());

        var key = MediaKeys.avatar(claims.organizationId(), upload.contentType());
        mediaStorage.store(key, new ByteArrayInputStream(upload.content()), upload.contentType(), upload.size());

        userStore.updateAvatarOverride(AvatarOverrideUpdate.builder().organizationId(claims.organizationId())
                .userId(claims.userId()).key(key).build(), clock.instant());
        return user.avatarOverrideKey();
    }

    @Transactional
    public String clear() {

        var claims = claimsReader.read();
        var user = requireProfile(claims.organizationId(), claims.userId());

        userStore.clearAvatarOverride(claims.organizationId(), claims.userId(), clock.instant());
        return user.avatarOverrideKey();
    }

    private UserAccount requireProfile(String organizationId, String userId) {

        if (organizationId == null) {
            throw new ValidationException("NO_ORGANIZATION", "An avatar belongs to a profile in an organization");
        }
        return userStore.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new NotFoundException("No profile for " + userId));
    }

    /**
     * Deletes the blob a {@link #set(ImageUpload)} or {@link #clear()} call superseded. Called only after that call has
     * returned, never from inside its transaction: object storage has no rollback to undo a delete issued too early. An
     * orphaned blob is worth a warning, never a failed request - the profile already points elsewhere by the time this
     * runs.
     */
    public void deletePreviousBlob(String key) {

        if (key == null) {
            return;
        }
        try {
            mediaStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Orphaned media blob '{}' - storage delete failed after avatar override change", key, e);
        }
    }
}
