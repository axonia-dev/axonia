package dev.axonia.expertise.application.port.out;

import java.util.List;

import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SourceType;

/**
 * The expertise signals projected from activity, and the curation moves of members' hidden areas and dismissals that
 * read the signals and declarations as they stand and so must run before the projection's own rewrites.
 */
public interface SignalProjection {

    /**
     * The hidden areas and dismissals that must follow a split of {@code variant} out of {@code canonical}. Call before
     * the alias is removed.
     */
    void carryOverSplit(String organizationId, long communityId, String variant, String canonical);

    /**
     * The hidden areas that must follow this community's {@code from} onto {@code to}, for a rename or a merge. Call
     * before any signal, declaration or alias changes.
     */
    void carryHiddenArea(String organizationId, long communityId, String from, String to);

    /**
     * The hidden areas this community's delete of {@code tag} leaves no reason for. Call before anything is removed.
     */
    void dropHiddenArea(String organizationId, long communityId, String tag);

    /**
     * Rewrites the projection to match a curator's rename. The dismissals move first, while they can still be matched
     * to the signals they were made about.
     */
    void renameTag(String organizationId, long communityId, String from, String to);

    /** Drops every signal the community carries for the tag a curator retired, and the dismissals made about them. */
    void removeTag(String organizationId, long communityId, String tag);

    /**
     * Replaces all expertise signals for the given {@link SignalSource} with the rows derived from {@code rows}.
     * Normalises and deduplicates before inserting.
     */
    void replaceSource(SignalSource source, List<SignalRow> rows);

    /** Deletes all expertise signals for {@code (organizationId, sourceType, sourceId)}. */
    void deleteSource(String organizationId, SourceType sourceType, Long sourceId);
}
