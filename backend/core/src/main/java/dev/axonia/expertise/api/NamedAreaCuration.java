package dev.axonia.expertise.api;

import java.util.List;

/**
 * A curator's grouping of a community's canonical tags into named areas, for callers outside the expertise module.
 * Every call requires curation access to the community. A tag belongs to at most one named area, so assigning it moves
 * it out of any other.
 */
public interface NamedAreaCuration {

    /** Every named area of the community with its tags, ordered by name. */
    List<CuratedNamedArea> namedAreas(String slug);

    CuratedNamedArea create(String slug, String name);

    CuratedNamedArea rename(String slug, long id, String name);

    /**
     * Deletes the named area. Its tags stay in the vocabulary and belong to no named area anymore.
     *
     * @return the named area as it was, with the tags it released
     */
    CuratedNamedArea delete(String slug, long id);

    /** Assigns every tag, each under its canonical, or none of them. */
    NamedAreaAssignment assign(String slug, long id, List<String> tags);

    /** Takes the tag, under its canonical, out of whichever named area holds it. */
    TagUnassignment unassign(String slug, String tag);
}
