package dev.axonia.recognition.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.ImpactVisits;
import dev.axonia.recognition.application.port.out.NewLoveQuery;
import dev.axonia.recognition.application.port.out.NewLoveTallies;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.application.port.out.RecognitionStore;
import dev.axonia.recognition.domain.NewLoveTally;
import dev.axonia.recognition.domain.RecognitionMother;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ImpactReplayServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00.123456Z");
    private static final Instant AS_OF = Instant.parse("2026-10-17T12:00:00.123Z");
    private static final Instant FLOOR = Instant.parse("2026-09-17T12:00:00.123Z");

    @Mock
    private RecognitionStore recognitionStore;

    @Mock
    private SlotSourceAccess slotSourceAccess;

    @Mock
    private ArtifactSourceAccess artifactSourceAccess;

    @Mock
    private NewLoveTallies newLoveTallies;

    @Mock
    private ImpactVisits impactVisits;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    private ImpactReplayService sut;

    @BeforeEach
    void setUp() {

        sut = new ImpactReplayService(
                new CreditedRecognitions(recognitionStore, slotSourceAccess, artifactSourceAccess), newLoveTallies,
                impactVisits, currentUser, organizationContext, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenMember() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
    }

    private void givenCredited() {

        givenMember();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of(100L, 101L));
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of(200L));
        when(recognitionStore.findAll("default", ContributionType.SLOT_PRESENTED, List.of(100L, 101L)))
                .thenReturn(List.of(RecognitionMother.any().contributionId(100L).communityId(7L).build(),
                        RecognitionMother.any().contributionId(101L).communityId(8L).build()));
        when(recognitionStore.findAll("default", ContributionType.ARTIFACT_CAPTURED, List.of(200L)))
                .thenReturn(List.of(RecognitionMother.any().contributionType(ContributionType.ARTIFACT_CAPTURED)
                        .contributionId(200L).communityId(7L).build()));
    }

    private void givenNothingCredited() {

        givenMember();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of());
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of());
    }

    private static NewLoveQuery query(ContributionType type, List<Long> ids) {

        return NewLoveQuery.builder().organizationId("default").userId("alice").contributionType(type)
                .contributionIds(ids).floor(FLOOR).asOf(AS_OF).build();
    }

    private static NewLoveTally tally(ContributionType type, Long id, long loves, boolean capped) {

        return NewLoveTally.builder().contributionType(type).contributionId(id).communityId(7L).newLoves(loves)
                .capped(capped).build();
    }

    @Test
    void newLovesQueriesBothTypesOverTheWindowAndOrsTheCap() {

        // given
        givenCredited();
        when(newLoveTallies.since(query(ContributionType.SLOT_PRESENTED, List.of(100L, 101L))))
                .thenReturn(List.of(tally(ContributionType.SLOT_PRESENTED, 100L, 2, false)));
        when(newLoveTallies.since(query(ContributionType.ARTIFACT_CAPTURED, List.of(200L))))
                .thenReturn(List.of(tally(ContributionType.ARTIFACT_CAPTURED, 200L, 1, true)));

        // when
        var actual = sut.newLoves(Optional.empty());

        // then
        assertThat(actual).isEqualTo(NewLoves.builder().asOf(AS_OF).capped(true)
                .items(List.of(new NewLoveItem(ContributionType.SLOT_PRESENTED, 100L, 2),
                        new NewLoveItem(ContributionType.ARTIFACT_CAPTURED, 200L, 1)))
                .build());
    }

    @Test
    void newLovesInOneCommunityQueriesOnlyItsContributions() {

        // given
        givenCredited();
        when(newLoveTallies.since(query(ContributionType.SLOT_PRESENTED, List.of(100L))))
                .thenReturn(List.of(tally(ContributionType.SLOT_PRESENTED, 100L, 3, false)));
        when(newLoveTallies.since(query(ContributionType.ARTIFACT_CAPTURED, List.of(200L)))).thenReturn(List.of());

        // when
        var actual = sut.newLoves(Optional.of(7L));

        // then
        assertThat(actual.capped()).isFalse();
        assertThat(actual.items()).containsExactly(new NewLoveItem(ContributionType.SLOT_PRESENTED, 100L, 3));
    }

    @Test
    void newLovesWithNothingCreditedAnswersEmptyWithoutCounting() {

        // given
        givenNothingCredited();

        // when
        var actual = sut.newLoves(Optional.empty());

        // then
        assertThat(actual).isEqualTo(NewLoves.builder().asOf(AS_OF).capped(false).items(List.of()).build());
        verify(newLoveTallies, never()).since(any());
    }

    @Test
    void markSeenWithoutACommunityMarksEveryCreditedCommunityOnce() {

        // given
        givenCredited();

        // when
        sut.markSeen(new MarkSeenRequest(AS_OF, null));

        // then
        verify(impactVisits).markSeen("default", "alice", List.of(7L, 8L), AS_OF);
    }

    @Test
    void markSeenInOneCommunityMarksOnlyIt() {

        // given
        givenMember();

        // when
        sut.markSeen(new MarkSeenRequest(AS_OF, 9L));

        // then
        verify(impactVisits).markSeen("default", "alice", List.of(9L), AS_OF);
    }

    @Test
    void markSeenRefusesAnAsOfAfterTheClock() {

        // when
        var actual = catchThrowable(() -> sut.markSeen(new MarkSeenRequest(NOW.plusMillis(1), null)));

        // then
        assertThat(actual).isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FUTURE_AS_OF");
        verify(impactVisits, never()).markSeen(anyString(), anyString(), anyCollection(), any());
    }

    @Test
    void markSeenWithNothingCreditedAndNoCommunityWritesNothing() {

        // given
        givenNothingCredited();

        // when
        sut.markSeen(new MarkSeenRequest(AS_OF, null));

        // then
        verify(impactVisits, never()).markSeen(anyString(), anyString(), anyCollection(), any());
    }
}
