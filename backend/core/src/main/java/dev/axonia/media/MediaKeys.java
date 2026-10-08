package dev.axonia.media;

import java.util.UUID;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * The shapes a storage key takes. One class mints every key and recognises every key the media route may serve, so a
 * shape written somewhere and a shape served cannot drift apart: a key minted here is servable, or the mint fails.
 */
@UtilityClass
public class MediaKeys {

    public static final int MAX_LENGTH = 512;

    private static final String FILE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)";
    private static final String OWNER = "[A-Za-z0-9_~-][A-Za-z0-9._~-]*";
    private static final Pattern SERVABLE = Pattern
            .compile("(avatars|reactions)/" + OWNER + "/" + FILE + "|communities/[0-9]+/(badge|hero)/" + FILE);

    public String avatar(String organizationId, String contentType) {

        return mint("avatars/" + organizationId, contentType);
    }

    public String reaction(String organizationId, String contentType) {

        return mint("reactions/" + organizationId, contentType);
    }

    public String communityAsset(long communityId, String segment, String contentType) {

        return mint("communities/" + communityId + "/" + segment, contentType);
    }

    public boolean isServable(String key) {

        return key.length() <= MAX_LENGTH && SERVABLE.matcher(key).matches();
    }

    private String mint(String prefix, String contentType) {

        var key = prefix + "/" + UUID.randomUUID() + MediaTypes.extensionFor(contentType);
        if (!isServable(key)) {
            throw new IllegalArgumentException(
                    "No image can be stored under " + prefix + ": the media route would never serve it");
        }
        return key;
    }
}
