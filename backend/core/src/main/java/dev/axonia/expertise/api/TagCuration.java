package dev.axonia.expertise.api;

/**
 * A curator's hold on a community's tags, for callers outside the expertise module. Both calls require curation access
 * to the community.
 */
public interface TagCuration {

    /** What the tag is holding up across the community's content and its members' declarations. */
    TagUsageReport usage(String slug, String tag);

    /** Strips the tag from every piece of the community's content and from the expertise built on it. */
    void delete(String slug, String tag);
}
