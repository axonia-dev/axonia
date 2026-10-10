package dev.axonia.roundup.application;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.media.api.AbsoluteMediaLinks;
import dev.axonia.roundup.api.spi.ArtifactActivity;
import dev.axonia.roundup.api.spi.CommunityActivity;
import dev.axonia.roundup.api.spi.CommunityActivity.CommunitySummary;
import dev.axonia.roundup.api.spi.SessionActivity;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.TopicActivity;
import dev.axonia.roundup.domain.Quarter;
import dev.axonia.roundup.domain.QuarterActivity;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.roundup.domain.RoundupFacts;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;

/**
 * Reads one closed quarter through the four activity ports and hands back a {@link QuarterActivity}
 * {@link RoundupFacts#of} can turn into the template's fact map. The single place either caller reads from: the
 * producer on its way to the outbox, and {@link RoundupPreviewer} when nothing is enqueued yet and it must show what a
 * send would look like. Two assemblies would eventually disagree, and the one place that disagreement would surface is
 * a preview that lies about the message it stands in for.
 *
 * <p>
 * Every read is a pure function of {@code (organizationId, quarter)} - no clock, no {@code now}. That is what makes the
 * preview's fallback reproducible: two previews of the same quarter taken a week apart differ only where the underlying
 * data itself changed, never because the assembly asked a different question the second time.
 *
 * <p>
 * "What is coming" is the quarter AFTER the one being reported, for the same reason. A window running from {@code now}
 * would shrink between the produce tick and any later preview, so a reader and an administrator checking what the
 * reader got would see different lists of upcoming sessions.
 *
 * <p>
 * Every port here is row-level-secured on {@code app.current_org} and answers an empty list or a zero rather than
 * failing when that GUC is unset - see each port's own javadoc. This class holds no defence of its own against that:
 * both callers are responsible for being inside the right organization scope before they call, the producer through
 * {@code OrganizationScope.runAs} and the previewer through the request's own scoped connection.
 *
 * <p>
 * {@code held} is read exactly once. Its session ids are then the SAME list handed to
 * {@link TopicActivity#tagsForSessions}, and the same sessions - paired with the community each one belongs to - are
 * handed to {@link ArtifactActivity#capturedForSessions}, rather than either port being asked about the quarter's
 * window in its own right. Two reads of "the quarter's sessions" could in principle answer two different sets - a
 * session closed out between the two calls, say - and captured knowledge or topics describing a different set of
 * sessions from the one the letter lists is exactly the disagreement this class exists to rule out. That one list is
 * narrowed to the communities {@link CommunityActivity#activeCommunities} still names before either port sees it - see
 * {@link #namedSessions} for why that has to happen HERE rather than downstream.
 */
@Component
@RequiredArgsConstructor
public class RoundupFactsAssembler {

    private static final String COMMUNITIES_PATH = "/communities";

    private final CommunityActivity communityActivity;
    private final SessionActivity sessionActivity;
    private final ArtifactActivity artifactActivity;
    private final TopicActivity topicActivity;
    private final RoundupLimits properties;
    private final PublicUrls publicUrlProperties;
    private final AbsoluteMediaLinks absoluteMediaLinks;

    public QuarterActivity read(String organizationId, String organizationName, Quarter quarter) {

        var lookaheadEnd = quarter.next().endExclusive();
        var communities = communityActivity.activeCommunities(organizationId);
        var held = sessionActivity.heldInPeriod(organizationId, quarter.start(), quarter.endExclusive());
        var named = namedSessions(held, communities);
        var sessionIds = named.stream().map(HeldSession::sessionId).toList();
        var sessionCommunityIds = named.stream()
                .collect(Collectors.toMap(HeldSession::sessionId, HeldSession::communityId));
        return QuarterActivity.builder().organizationName(organizationName).quarter(quarter)
                .communities(communities.stream().map(this::named).toList()).held(held)
                .upcoming(sessionActivity.scheduledAfter(organizationId, quarter.endExclusive(), lookaheadEnd))
                .firstTimePresenterCount(sessionActivity.firstTimePresenterCount(organizationId, quarter.start(),
                        quarter.endExclusive()))
                .captured(artifactActivity.capturedForSessions(organizationId, sessionCommunityIds))
                .topics(topicActivity.tagsForSessions(organizationId, sessionIds))
                .openTalkCount(sessionActivity.openTalkCount(organizationId, quarter.endExclusive(), lookaheadEnd))
                .communityCap(properties.communityCap()).sessionsPerCommunityCap(properties.sessionsPerCommunityCap())
                .topicCap(properties.topicCap()).communitiesUrl(publicUrlProperties.publicBaseUrl() + COMMUNITIES_PATH)
                .build();
    }

    /**
     * One community, translated from what the port answers into what the letter can render. <b>The whole of the
     * translation is the badge</b>: {@code badgeKey} is a storage object key, and every other consumer of it in the
     * codebase - the public hub card, an invite, the registry card, the community page itself - resolves it to an
     * address before anything renders it, and a mail does so through {@link AbsoluteMediaLinks#urlFor}, being read
     * outside the application's pages. The roundup used to be the exception, passing the raw key down to a template
     * that could only ever have produced a broken image from it.
     *
     * <p>
     * It happens HERE rather than in {@link RoundupFacts} because that class is a pure function with no collaborators
     * by design, and rather than in {@code CommunityActivityAdapter} because the port's contract is deliberately
     * key-shaped: {@code CommunityActivity} answers what the database holds, and how a key becomes a URL is a rendering
     * concern belonging to whoever is about to render. A community that never uploaded a badge stays null and falls
     * back to the initials monogram, the same null handling the four existing call sites use.
     */
    private NamedCommunity named(CommunitySummary summary) {

        return new NamedCommunity(summary.id(), summary.name(),
                Optional.ofNullable(summary.badgeKey()).map(absoluteMediaLinks::urlFor).orElse(null),
                summary.accentColor());
    }

    /**
     * The quarter's sessions, minus any held in a community {@link CommunityActivity#activeCommunities} no longer
     * names. {@link RoundupFacts} applies the same filter again when it assembles the letter, and this one is not
     * redundant with it: <b>{@code TopicActivity#tagsForSessions} answers pre-aggregated {@code (tag, count)} rows with
     * no session key on them</b>, so a tag that only ever appeared in an archived community's sessions cannot be
     * subtracted downstream by anything. Asked here, the archived community's sessions never enter the aggregate in the
     * first place, and the cloud describes exactly the sessions the cards list.
     *
     * <p>
     * The captured artifacts are narrowed by the same list for consistency rather than out of necessity - they DO carry
     * a session id, and {@code RoundupFacts} filters them again - but reading knowledge for sessions the letter will
     * never name is work nobody wants the answer to.
     *
     * <p>
     * Kept as the full {@code HeldSession} list, not narrowed to bare ids, because {@link #read} needs both a session
     * id (for {@code topicActivity}) and each session's community id (for {@code artifactActivity} - see that port's
     * javadoc for why) derived from the SAME filtered set, never two separately-filtered ones that could in principle
     * disagree.
     */
    private static List<HeldSession> namedSessions(List<HeldSession> held, List<CommunitySummary> communities) {

        var namedCommunityIds = communities.stream().map(CommunitySummary::id).collect(Collectors.toSet());
        return held.stream().filter(session -> namedCommunityIds.contains(session.communityId())).toList();
    }
}
