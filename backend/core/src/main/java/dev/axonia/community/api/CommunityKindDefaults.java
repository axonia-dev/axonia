package dev.axonia.community.api;

/** Seeds a fresh Organization's community-kind vocabulary, inside the caller's transaction and RLS scope. */
public interface CommunityKindDefaults {

    void seedFor(String organizationId);
}
