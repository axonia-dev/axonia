package dev.axonia.events.api;

/** A session under the name it is shown by everywhere, with the community it belongs to. */
public record NamedSession(long communityId, String name) {
}
