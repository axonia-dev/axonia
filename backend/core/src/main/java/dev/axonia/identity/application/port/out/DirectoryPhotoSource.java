package dev.axonia.identity.application.port.out;

import java.util.Optional;

import dev.axonia.identity.domain.AvatarPhoto;

/** A member's photo in their organization's Microsoft directory, read through Graph for one tenant. */
public interface DirectoryPhotoSource {

    /** Photo descriptor without the bytes; the etag decides whether a download is needed. */
    record PhotoMetadata(String etag) {
    }

    /** Photo metadata for a directory user; empty when the user has no photo. */
    Optional<PhotoMetadata> photoMetadata(String externalId, String aadTenantId);

    /** The photo bytes; empty when there is no photo or no body. */
    Optional<AvatarPhoto> fetchPhoto(String externalId, String aadTenantId);
}
