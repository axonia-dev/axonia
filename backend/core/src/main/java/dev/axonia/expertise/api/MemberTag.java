package dev.axonia.expertise.api;

import java.time.Instant;

/**
 * A member's own statement about a tag in one community, stored as typed and dated by the application: an expertise
 * declaration, a learning intent. {@link MemberTagStore} holds the writes every table of them shares.
 */
public interface MemberTag {

    long communityId();

    String userId();

    String tag();

    Instant declaredAt();
}
