package dev.axonia.recognition.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.adapter.out.persistence.JdbcImpactVisits;
import dev.axonia.recognition.adapter.out.persistence.JdbcLoveStore;
import dev.axonia.recognition.adapter.out.persistence.JdbcNewLoveTallies;
import dev.axonia.recognition.adapter.out.persistence.JdbcRecognitionStore;
import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.ArtifactTypeAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.CommunityAccess;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.OrganizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The replay read and the marker write together on real Postgres. Same context as RecognitionServiceJdbcTest. */
@Import({RecognitionService.class, ImpactReplayService.class, CreditedRecognitions.class, JdbcRecognitionStore.class,
        JdbcLoveStore.class, JdbcNewLoveTallies.class, JdbcImpactVisits.class})
class ImpactReplayServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final String ME = "presenter";
    private static final long COMMUNITY_A = 870_001L;
    private static final long COMMUNITY_B = 870_002L;

    @Autowired
    private ImpactReplayService sut;

    @Autowired
    private RecognitionService recognitionService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private CommunityAccess communityAccess;

    @MockitoBean
    private SlotSourceAccess slotSourceAccess;

    @MockitoBean
    private ArtifactSourceAccess artifactSourceAccess;

    @MockitoBean
    private ArtifactTypeAccess artifactTypeAccess;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoBean
    private OrganizationContext organizationContext;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(artifactSourceAccess.artifactsCapturedBy(ORG, ME)).thenReturn(List.of());
    }

    private void as(String userId) {

        when(currentUser.userId()).thenReturn(userId);
    }

    private static Instant daysAgo(long days) {

        return Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    }

    private void presenting(Long... slotIds) {

        when(slotSourceAccess.slotsPresentedBy(ORG, ME)).thenReturn(List.of(slotIds));
    }

    private Long recognize(long slotId, long communityId) {

        return jdbc.queryForObject("""
                INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id,
                    community_id, title, occurred_at, created_at, updated_at, version)
                VALUES (:org, :type, :slot, :community, 'Talk', now(), now(), now(), 0) RETURNING id
                """, Map.of("org", ORG, "type", ContributionType.SLOT_PRESENTED.name(), "slot", slotId, "community",
                communityId), Long.class);
    }

    private Long love(Long recognizedContributionId, String lover, Instant createdAt) {

        return jdbc.queryForObject("""
                INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id,
                    created_at, updated_at, version)
                VALUES (:org, :rc, :lover, :at, :at, 0) RETURNING id
                """,
                Map.of("org", ORG, "rc", recognizedContributionId, "lover", lover, "at", Timestamp.from(createdAt)),
                Long.class);
    }

    @Test
    void markingOneCommunitySeenLeavesTheOthersLovesToReplay() {

        // given
        love(recognize(1L, COMMUNITY_A), "u1", daysAgo(2));
        love(recognize(2L, COMMUNITY_B), "u1", daysAgo(2));
        presenting(1L, 2L);
        sut.markSeen(new MarkSeenRequest(sut.newLoves(Optional.empty()).asOf(), COMMUNITY_A));

        // when
        var actual = sut.newLoves(Optional.empty());

        // then
        assertThat(actual.items()).containsExactly(new NewLoveItem(ContributionType.SLOT_PRESENTED, 2L, 1));
    }

    @Test
    void aCoPresentersOwnLoveIsNotNews() {

        // given
        var slot = recognize(1L, COMMUNITY_A);
        love(slot, ME, daysAgo(2));
        love(slot, "third", daysAgo(2));
        presenting(1L);

        // when
        var actual = sut.newLoves(Optional.empty());

        // then
        assertThat(actual.items()).containsExactly(new NewLoveItem(ContributionType.SLOT_PRESENTED, 1L, 1));
    }

    /** Through the real love and unlove: an unlove deletes the row and a love again is stamped afresh. */
    @Test
    void aLoveTakenBackAndGivenAgainAfterTheMarkerCountsOnce() {

        // given
        when(slotSourceAccess.slotContribution(ORG, 1L))
                .thenReturn(Optional.of(ContributionSource.builder().communityId(COMMUNITY_A)
                        .contributorUserIds(List.of(ME)).title("Talk").occurredAt(daysAgo(20)).build()));
        when(communityAccess.isMember(COMMUNITY_A, "u1")).thenReturn(true);
        presenting(1L);
        var request = new LoveRequest(ContributionType.SLOT_PRESENTED, 1L);
        as("u1");
        var first = recognitionService.love(request);
        jdbc.update("UPDATE contribution_love SET created_at = :at WHERE id = :id",
                Map.of("at", Timestamp.from(daysAgo(10)), "id", first.id()));
        as(ME);
        sut.markSeen(new MarkSeenRequest(daysAgo(5), null));
        as("u1");
        recognitionService.unlove(ContributionType.SLOT_PRESENTED, 1L);
        recognitionService.love(request);
        as(ME);

        // when
        var actual = sut.newLoves(Optional.empty());

        // then
        assertThat(actual.items()).containsExactly(new NewLoveItem(ContributionType.SLOT_PRESENTED, 1L, 1));
        assertThat(actual.capped()).isFalse();
    }

    @Test
    void markingSeenAtTheReadsAsOfLeavesNothingNew() {

        // given
        love(recognize(1L, COMMUNITY_A), "u1", daysAgo(2));
        love(recognize(2L, COMMUNITY_B), "u2", daysAgo(1));
        presenting(1L, 2L);
        var read = sut.newLoves(Optional.empty());
        sut.markSeen(new MarkSeenRequest(read.asOf(), null));

        // when
        var actual = sut.newLoves(Optional.empty());

        // then
        assertThat(read.items()).hasSize(2);
        assertThat(read.capped()).isTrue();
        assertThat(actual.items()).isEmpty();
        assertThat(actual.capped()).isFalse();
    }
}
