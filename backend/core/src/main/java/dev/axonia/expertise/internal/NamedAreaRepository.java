package dev.axonia.expertise.internal;

import java.util.List;

import dev.axonia.expertise.NamedArea;

/**
 * The named areas of one community and the canonical tags each holds. A tag sits in at most one named area; the
 * database enforces it.
 */
public interface NamedAreaRepository {

    /** Every named area of the community with its tags, ordered by name then id. A named area with no tags is kept. */
    List<NamedArea> findAll(String organizationId, long communityId);

    long create(String organizationId, long communityId, String name);

    /** @return false when no named area of this community has that id */
    boolean rename(String organizationId, long communityId, long id, String name);

    /** @return false when no named area of this community has that id */
    boolean delete(String organizationId, long communityId, long id);

    /**
     * Moves the tag if another named area holds it: the constraint is the point, not an error to surface.
     *
     * @return false when no named area of this community has that id
     */
    boolean place(String organizationId, long communityId, long id, String canonicalTag);

    void remove(String organizationId, long communityId, String canonicalTag);

    /** Every membership row naming this tag, for the paths that end a tag's name. */
    void removeNaming(String organizationId, long communityId, String tag);

    /**
     * Carries a tag's membership to the name it now reads as, after a rename or a merge. When that name already sits in
     * a named area, that stands.
     */
    void renameTag(String organizationId, long communityId, String from, String to);
}
