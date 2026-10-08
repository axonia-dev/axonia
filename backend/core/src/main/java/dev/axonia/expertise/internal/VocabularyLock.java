package dev.axonia.expertise.internal;

/**
 * The lock a community's vocabulary is rewritten under. A curator's merge, split, rename and delete take it
 * exclusively; every writer of records stored as typed and read through the community's merges (a declaration, a
 * learning intent) takes it shared before it canonicalizes, so no curator rewrites the names under it. Held until the
 * transaction ends.
 */
public interface VocabularyLock {

    void share(String org, long communityId);

    void lock(String org, long communityId);
}
