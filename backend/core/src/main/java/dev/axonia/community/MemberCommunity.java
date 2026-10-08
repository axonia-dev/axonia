package dev.axonia.community;

/** A community a member belongs to, as another module links to and names it. */
public record MemberCommunity(long id, String slug, String name) {
}
