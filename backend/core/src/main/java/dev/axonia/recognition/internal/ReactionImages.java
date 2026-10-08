package dev.axonia.recognition.internal;

import java.io.ByteArrayInputStream;

import org.springframework.stereotype.Component;

import dev.axonia.media.ImageUpload;
import dev.axonia.media.ImageValidator;
import dev.axonia.media.MediaKeys;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stores a custom reaction's image and hands back its key.
 *
 * <p>
 * Public-by-URL, as community badges and hero banners are: a reaction renders beside somebody's name on a page anyone
 * in the Organization can open, and an authenticated blob fetch per chip is not a thing that would ship.
 *
 * <p>
 * 128 KB rather than the avatar's 2 MB: this renders at seventeen pixels. {@link ImageValidator} rejects GIF, which
 * settles animation without a separate argument.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionImages {

    static final long MAX_BYTES = 128L * 1024;

    private final MediaStorage mediaStorage;

    public String store(String organizationId, ImageUpload upload) {

        ImageValidator.validate(upload, MAX_BYTES);
        var key = MediaKeys.reaction(organizationId, upload.contentType());
        mediaStorage.store(key, new ByteArrayInputStream(upload.content()), upload.contentType(), upload.size());
        return key;
    }

    /**
     * Called only after the transaction that dropped the row has committed. A blob orphaned by a failure here is
     * strictly better than a row pointing at a blob that is already gone.
     */
    public void deleteBlob(String key) {

        if (key == null) {
            return;
        }
        try {
            mediaStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete reaction image {}", key, e);
        }
    }
}
