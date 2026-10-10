package dev.axonia.expertise.domain;

/** A community resolved from its slug, with whether it is archived. */
public record CommunityRef(long id, boolean archived) {
}
