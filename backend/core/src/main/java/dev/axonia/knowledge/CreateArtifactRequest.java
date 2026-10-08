package dev.axonia.knowledge;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A contribution to a community's library. {@code contributor} is an optional free-text reference - a display name, an
 * email, or a user id - to the member whose work this is. Omitted, the contributor is the caller, which is the live
 * path. Supplied, it is an organizer's act of crediting someone else, and a reference that does not resolve to exactly
 * one member of the community refuses the write rather than crediting the caller by default.
 */
public record CreateArtifactRequest(@NotBlank String title, @NotNull Long typeId,
        @NotBlank @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String url, String description,
        Set<String> tags, ProvenanceRequest provenance, String contributor) {
}
