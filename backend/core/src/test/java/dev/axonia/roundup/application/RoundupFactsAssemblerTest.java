package dev.axonia.roundup.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.media.api.AbsoluteMediaLinks;
import dev.axonia.roundup.api.spi.ArtifactActivity;
import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.CommunityActivity;
import dev.axonia.roundup.api.spi.CommunityActivity.CommunitySummary;
import dev.axonia.roundup.api.spi.SessionActivity;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.UpcomingSession;
import dev.axonia.roundup.api.spi.TopicActivity;
import dev.axonia.roundup.configuration.RoundupProperties;
import dev.axonia.roundup.domain.Quarter;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.roundup.domain.RoundupFacts;
import dev.axonia.shared.configuration.PublicUrlProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The windows this class asks each port about, which nothing downstream can check for it: {@link RoundupFacts} is
 * handed whatever came back and has no way to tell a quarter read over the wrong three months from a quiet one.
 */
@MockitoSettings
class RoundupFactsAssemblerTest {

    private static final String ORGANIZATION_ID = "acme";
    private static final String ORGANIZATION_NAME = "Acme";
    private static final Quarter QUARTER = new Quarter(2026, 2);
    private static final Instant QUARTER_START = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant QUARTER_END = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant NEXT_QUARTER_END = Instant.parse("2026-10-01T00:00:00Z");
    private static final int COMMUNITY_CAP = 8;
    private static final int SESSIONS_PER_COMMUNITY_CAP = 4;
    private static final int TOPIC_CAP = 12;

    @Mock
    private CommunityActivity communityActivity;
    @Mock
    private SessionActivity sessionActivity;
    @Mock
    private ArtifactActivity artifactActivity;
    @Mock
    private TopicActivity topicActivity;
    @Mock
    private AbsoluteMediaLinks absoluteMediaLinks;

    private RoundupFactsAssembler assembler;

    @BeforeEach
    void buildAssembler() {

        assembler = new RoundupFactsAssembler(communityActivity, sessionActivity, artifactActivity, topicActivity,
                new RoundupProperties(Duration.ofDays(2), COMMUNITY_CAP, SESSIONS_PER_COMMUNITY_CAP, TOPIC_CAP),
                new PublicUrlProperties("https://axonia.example"), absoluteMediaLinks);
    }

    @Test
    void readsEveryFigureOverTheQuarterItWasAskedAbout() {

        var held = List
                .of(HeldSession.builder().sessionId(1L).communityId(7L).title("Hexagons in anger").heldAt(QUARTER_START)
                        .talkCount(2).talkTitles(List.of()).presenterNames(List.of("Alice Dubois")).build());
        var communities = List.of(backendGuild());
        var captured = IntStream.range(0, 47)
                .mapToObj(i -> new CapturedArtifact(1L, "Artifact " + i, "https://example.test/" + i, "Recording"))
                .toList();
        given(communityActivity.activeCommunities(ORGANIZATION_ID)).willReturn(communities);
        given(sessionActivity.heldInPeriod(ORGANIZATION_ID, QUARTER_START, QUARTER_END)).willReturn(held);
        given(sessionActivity.firstTimePresenterCount(ORGANIZATION_ID, QUARTER_START, QUARTER_END)).willReturn(3);
        given(artifactActivity.capturedForSessions(ORGANIZATION_ID, Map.of(1L, 7L))).willReturn(captured);

        var actual = assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        assertThat(actual.organizationName()).isEqualTo(ORGANIZATION_NAME);
        assertThat(actual.quarter()).isEqualTo(QUARTER);
        assertThat(actual.communities()).containsExactly(new NamedCommunity(7L, "Backend Guild", null, null));
        assertThat(actual.held()).isEqualTo(held);
        assertThat(actual.firstTimePresenterCount()).isEqualTo(3);
        assertThat(actual.captured()).isEqualTo(captured);
    }

    /**
     * {@code held} is read exactly once, and both ports keyed by session id are asked about the very ids it names -
     * never a second reading of the window that could, in principle, answer a different set of sessions.
     */
    @Test
    void capturedKnowledgeIsReadForTheQuartersOwnSessions_notForTheQuarterAsAWindow() {

        given(communityActivity.activeCommunities(ORGANIZATION_ID)).willReturn(List.of(backendGuild()));
        when(sessionActivity.heldInPeriod(ORGANIZATION_ID, QUARTER.start(), QUARTER.endExclusive()))
                .thenReturn(List.of(heldSession(41L, 7L), heldSession(42L, 7L)));

        assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        verify(artifactActivity).capturedForSessions(ORGANIZATION_ID, Map.of(41L, 7L, 42L, 7L));
        verify(topicActivity).tagsForSessions(ORGANIZATION_ID, List.of(41L, 42L));
    }

    /**
     * The one filter that cannot be applied downstream. {@code TopicActivity#tagsForSessions} answers pre-aggregated
     * {@code (tag, count)} rows with no session key on them, so a tag used only in a session held by a community
     * archived since could never be subtracted again once it entered the aggregate - and the letter would print a cloud
     * of what the quarter was about above cards that name none of those sessions. Asking the port about the named
     * sessions alone is the only place that can be closed. The artifacts are narrowed with the same list, so neither
     * port is ever asked about a session the letter will not list.
     */
    @Test
    void aSessionHeldByACommunityNobodyCanNameAnyMoreIsNeverAskedAboutAtAll() {

        given(communityActivity.activeCommunities(ORGANIZATION_ID)).willReturn(List.of(backendGuild()));
        when(sessionActivity.heldInPeriod(ORGANIZATION_ID, QUARTER.start(), QUARTER.endExclusive()))
                .thenReturn(List.of(heldSession(41L, 7L), heldSession(99L, 9L)));

        assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        verify(artifactActivity).capturedForSessions(ORGANIZATION_ID, Map.of(41L, 7L));
        verify(topicActivity).tagsForSessions(ORGANIZATION_ID, List.of(41L));
    }

    /**
     * "What is coming" looks into the quarter AFTER the one being reported, derived from that quarter and never from
     * {@code now}: a window running from the current instant would shrink between the produce tick and any later
     * preview, so an administrator checking what a reader received would be shown a shorter list than the reader got.
     */
    @Test
    void looksAheadIntoTheQuarterThatFollowsTheOneItReports() {

        given(sessionActivity.scheduledAfter(ORGANIZATION_ID, QUARTER_END, NEXT_QUARTER_END))
                .willReturn(List.of(new UpcomingSession(7L, "Signals in React", QUARTER_END.plusSeconds(1))));

        var actual = assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        assertThat(actual.upcoming()).hasSize(1);
        then(sessionActivity).should().scheduledAfter(ORGANIZATION_ID, QUARTER_END, NEXT_QUARTER_END);
    }

    /** The ask uses the same lookahead window the upcoming list uses, never the quarter's own window. */
    @Test
    void theAskCountsOpenTalksOverTheSameLookaheadTheUpcomingListUses() {

        assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        verify(sessionActivity).openTalkCount(ORGANIZATION_ID, QUARTER.endExclusive(), QUARTER.next().endExclusive());
    }

    @Test
    void carriesEveryConfiguredCapAndTheOnePublicLinkTheMessageOffers() {

        var actual = assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        assertThat(actual.communityCap()).isEqualTo(COMMUNITY_CAP);
        assertThat(actual.sessionsPerCommunityCap()).isEqualTo(SESSIONS_PER_COMMUNITY_CAP);
        assertThat(actual.topicCap()).isEqualTo(TOPIC_CAP);
        assertThat(actual.communitiesUrl()).isEqualTo("https://axonia.example/communities");
    }

    /**
     * The one translation this class does on the way out, and the gap it closes. {@code CommunitySummary#badgeKey} is a
     * storage OBJECT KEY - every other consumer of it resolves the key to an address before rendering, the roundup
     * through {@code AbsoluteMediaLinks#urlFor} because a mail is read outside the application, and the roundup used to
     * be the exception, handing the raw key to a template whose {@code <img src>} could only ever have been broken.
     * Asserted through the port rather than by inspecting a string, so a resolution done by string concatenation here
     * instead of by {@code AbsoluteMediaLinks} would fail.
     */
    @Test
    void aCommunitysBadgeKeyIsResolvedToAPublicUrlBeforeItCanReachATemplate() {

        given(communityActivity.activeCommunities(ORGANIZATION_ID))
                .willReturn(List.of(new CommunitySummary(7L, "Backend Guild", "badges/7.png", "#1f9d63")));
        given(absoluteMediaLinks.urlFor("badges/7.png")).willReturn("https://cdn.axonia.example/badges/7.png");

        var actual = assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        assertThat(actual.communities()).containsExactly(
                new NamedCommunity(7L, "Backend Guild", "https://cdn.axonia.example/badges/7.png", "#1f9d63"));
    }

    /**
     * A community that never uploaded a badge keeps a null rather than acquiring a URL to nothing, and
     * {@code AbsoluteMediaLinks} is not asked at all - {@code urlFor(null)} is a question with no honest answer, and
     * the shell's initials monogram is the rendering for its absence.
     */
    @Test
    void aCommunityWithNoBadgeKeepsANullAndTheStorageIsNeverAsked() {

        given(communityActivity.activeCommunities(ORGANIZATION_ID)).willReturn(List.of(backendGuild()));

        var actual = assembler.read(ORGANIZATION_ID, ORGANIZATION_NAME, QUARTER);

        assertThat(actual.communities()).containsExactly(new NamedCommunity(7L, "Backend Guild", null, null));
        then(absoluteMediaLinks).shouldHaveNoInteractions();
    }

    private static CommunitySummary backendGuild() {

        return new CommunitySummary(7L, "Backend Guild", null, null);
    }

    private static HeldSession heldSession(long sessionId, long communityId) {

        return HeldSession.builder().sessionId(sessionId).communityId(communityId).title("A session")
                .heldAt(QUARTER_START).talkCount(1).talkTitles(List.of()).presenterNames(List.of()).build();
    }
}
