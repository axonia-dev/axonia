package dev.axonia.identity.application.port.out;

import java.util.Optional;

import dev.axonia.identity.domain.AvatarPhoto;

/** The photo behind an identity provider's {@code picture} claim URL, fetched under the avatar host policy. */
public interface ClaimPhotoSource {

    /** Empty when there is nothing to mirror; throws when the fetch failed and a later window should retry. */
    Optional<AvatarPhoto> fetch(String url);
}
