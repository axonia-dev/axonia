package dev.axonia.events;

import java.time.Instant;
import java.util.List;

import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import dev.axonia.community.CommunicationSettings;
import dev.axonia.community.CommunityIdentity;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.community.MemberMatch;
import dev.axonia.community.TopicManagementPolicy;

public interface CommunityAccess {

    long communityIdForSlug(String slug);

    /**
     * The identity a producer needs for a session's community: the slug a frontend route needs, plus the name and
     * accent the mail itself renders. A community's real page lives at {@code /communities/:slug/...}, never at a bare
     * id, so a caller minting a member-facing link needs the slug to build one that actually resolves rather than 404s
     * - and the name so the member can tell which of their communities the message is about.
     *
     * <p>
     * One call rather than three: all three values sit on the same row, so splitting them would multiply a single read
     * into several for no gain.
     *
     * <p>
     * Org-explicit only: every caller of this is a producer, which runs inside the communication sweep's
     * {@code OrganizationScope.runAs} and carries no JWT to read an implicit organization context from. There is
     * deliberately no implicit-organization overload here - one existed briefly and was removed once it reached zero
     * production callers, the same fate {@code communicationSettings(long)} and {@code isMember(long, String)} would
     * meet if their own HTTP-facing callers ever stopped needing them.
     */
    CommunityIdentity communityIdentity(String organizationId, long communityId);

    boolean isMember(long communityId, String userId);

    boolean isOrganizer(long communityId, String userId);

    ContributionPolicy contributionPolicy(long communityId);

    TopicManagementPolicy topicManagementPolicy(long communityId);

    /**
     * Whether this user may govern the community's outgoing mail - the one axis a session's own reminder and wrap-up
     * are gated on. Already evaluated against the caller, unlike {@link #contributionPolicy(long)}: the community
     * module answers "may this person" rather than handing back a record every caller would have to combine with an
     * organizer check of its own.
     */
    boolean canManageCommunication(long communityId, String userId);

    /** The community's communication settings, notably the wrap-up grace period, for closing a session. */
    CommunicationSettings communicationSettings(long communityId);

    /**
     * Org-explicit variant of {@link #communicationSettings(long)}, for a caller with no HTTP request to read an
     * organization context from - the communication drain's delivery gate, which runs each organization's due work
     * inside {@code OrganizationScope.runAs} and carries no JWT.
     */
    CommunicationSettings communicationSettings(String organizationId, long communityId);

    /**
     * Org-explicit variant of {@link #isMember(long, String)}, for the same no-JWT caller.
     */
    boolean isMember(String organizationId, long communityId, String userId);

    /**
     * Resolves a free-text member reference - a display name, an email, or a user id, as a tool being migrated away
     * from would spell it - to a member of the community, so a presenter or a proposer can be named rather than
     * guessed.
     *
     * <p>
     * The resolution belongs to {@code community}, which owns the roster: an address is never synthesized from a name,
     * because a wrong guess silently credits one person's talk to another and nobody would ever notice. A reference
     * that does not resolve to exactly one member of the community assigns nobody.
     */
    MemberMatch resolveMember(long communityId, String query);

    /**
     * Every member of the community, as bare user ids - org-explicit, same no-JWT caller as
     * {@link #communityIdentity(String, long)}. The audience for a community-wide notice such as
     * {@code SESSION_UPCOMING} or {@code SESSION_WRAP_UP}.
     */
    List<String> memberUserIds(String organizationId, long communityId);

    /**
     * The community's organizers, as bare user ids - org-explicit, same caller as {@link #memberUserIds}. Part of the
     * audience for the awaiting-close nudge alongside the session's facilitator.
     */
    List<String> organizerUserIds(String organizationId, long communityId);

    /**
     * One destination's status, resolved fresh: a destination removed or broken since a message was queued must stop
     * it, exactly as every other governance answer is read at send time rather than at fan-out. Org-explicit, same
     * no-JWT caller as {@link #memberUserIds}: {@code events.internal.ChatDestinationsAdapter} is reached from the
     * drain's delivery gate, inside {@code OrganizationScope.runAs}, which carries no JWT to resolve an implicit
     * organization context from.
     */
    ChatChannelStatus chatDestinationStatus(String organizationId, long destinationId);

    /**
     * This community's destination ids, for the drain's fan-out. Org-explicit, same no-JWT caller as
     * {@link #chatDestinationStatus}.
     */
    List<Long> chatDestinationIds(String organizationId, long communityId);

    /**
     * Records that a post reached the destination, resetting its failure streak. Org-explicit for the same no-JWT
     * caller as {@link #chatDestinationStatus}.
     */
    void recordChatDelivered(String organizationId, long destinationId, Instant at);

    /**
     * Records a failed post. {@code brokenAfter} consecutive failed attempts declare the destination broken - the count
     * belongs to the caller that knows the policy, not to the row that stores it. Org-explicit for the same no-JWT
     * caller as {@link #chatDestinationStatus}.
     */
    void recordChatFailure(String organizationId, long destinationId, ChatFailure failure, int brokenAfter);
}
