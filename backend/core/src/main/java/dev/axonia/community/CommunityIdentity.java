package dev.axonia.community;

/**
 * The three columns a caller outside this module needs off a community row without paying for the member count and the
 * kind label {@link Community} also carries, each of which costs a query of its own. Produced by
 * {@link CommunityService#identityById}, whose only callers are the communication sweep's producers: they mint a
 * session URL from the slug and put the name and the accent into the mail a member actually reads.
 *
 * <p>
 * {@code accentColor} is nullable, and deliberately not defaulted here. Choosing an accent is optional, and what an
 * un-accented community should look like is a rendering decision that belongs to whoever renders it.
 */
public record CommunityIdentity(String slug, String name, String accentColor) {
}
