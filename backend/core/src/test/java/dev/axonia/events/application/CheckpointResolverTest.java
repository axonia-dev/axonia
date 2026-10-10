package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.SeriesRecordMother;
import dev.axonia.events.domain.SessionRecordMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CheckpointResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-19T09:00:00Z");

    @Mock
    private SessionStore sessionStore;

    @Mock
    private SeriesStore seriesStore;

    private CheckpointResolver resolver;

    @BeforeEach
    void buildResolver() {

        resolver = new CheckpointResolver(sessionStore, seriesStore, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ActionItemRecord item(Long checkpointSessionId) {

        return ActionItemRecord.builder().organizationId("default").communityId(1L)
                .checkpointSessionId(checkpointSessionId).outcome(ActionItemOutcome.OPEN).build();
    }

    @Test
    void aNonNullCheckpointSessionIdIsPinnedToThatSessionEvenInThePast() {

        var pastSession = SessionRecordMother.any().id(5L).communityId(1L)
                .scheduledAt(Instant.parse("2020-01-01T10:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndId("default", 5L)).willReturn(Optional.of(pastSession));

        var checkpoint = resolver.resolve("default", item(5L));

        assertThat(checkpoint.pinned()).isTrue();
        assertThat(checkpoint.sessionId()).isEqualTo(5L);
        assertThat(checkpoint.scheduledAt()).isEqualTo(Instant.parse("2020-01-01T10:00:00Z"));
    }

    @Test
    void aNullCheckpointSessionIdLateBindsToTheSoonestUpcomingSessionUnpinned() {

        var upcoming = SessionRecordMother.any().id(9L).communityId(1L)
                .scheduledAt(Instant.parse("2026-10-01T18:00:00Z")).build();
        given(sessionStore.findSoonestUpcoming("default", 1L, NOW)).willReturn(Optional.of(upcoming));

        var checkpoint = resolver.resolve("default", item(null));

        assertThat(checkpoint.pinned()).isFalse();
        assertThat(checkpoint.sessionId()).isEqualTo(9L);
        assertThat(checkpoint.scheduledAt()).isEqualTo(Instant.parse("2026-10-01T18:00:00Z"));
    }

    @Test
    void aCommunityWithNoUpcomingSessionResolvesToNoCheckpointStillUnpinned() {

        given(sessionStore.findSoonestUpcoming("default", 1L, NOW)).willReturn(Optional.empty());

        var checkpoint = resolver.resolve("default", item(null));

        assertThat(checkpoint.pinned()).isFalse();
        assertThat(checkpoint.sessionId()).isNull();
        assertThat(checkpoint.scheduledAt()).isNull();
    }

    @Test
    void aSettledItemWithNoPinResolvesToNoSessionAtAll() {

        var item = item(null).toBuilder().outcome(ActionItemOutcome.DONE).build();

        var checkpoint = resolver.resolve("default", item);

        assertThat(checkpoint.sessionId()).isNull();
        assertThat(checkpoint.pinned()).isFalse();
        verify(sessionStore, never()).findSoonestUpcoming(any(), any(), any());
    }

    @Test
    void aPinnedCheckpointSessionThatNoLongerExistsIsAnIllegalState() {

        given(sessionStore.findByOrganizationIdAndId("default", 5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("default", item(5L))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5");
    }

    @Test
    void resolveAllBatchesOnePinnedSessionQueryAndOneSoonestQueryOverAMixedPage() {

        var pinnedSession = SessionRecordMother.any().id(5L).communityId(1L)
                .scheduledAt(Instant.parse("2020-01-01T10:00:00Z")).build();
        var soonest = SessionRecordMother.any().id(9L).communityId(1L)
                .scheduledAt(Instant.parse("2026-10-01T18:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndCommunityIdAndIdIn("default", 1L, List.of(5L)))
                .willReturn(List.of(pinnedSession));
        given(sessionStore.findSoonestUpcoming("default", 1L, NOW)).willReturn(Optional.of(soonest));
        var pinnedItem = ActionItemRecord.builder().id(101L).organizationId("default").communityId(1L)
                .checkpointSessionId(5L).outcome(ActionItemOutcome.OPEN).build();
        var unpinnedItem = ActionItemRecord.builder().id(102L).organizationId("default").communityId(1L)
                .outcome(ActionItemOutcome.OPEN).build();

        var checkpoints = resolver.resolveAll("default", 1L, List.of(pinnedItem, unpinnedItem));

        assertThat(checkpoints.get(101L).pinned()).isTrue();
        assertThat(checkpoints.get(101L).sessionId()).isEqualTo(5L);
        assertThat(checkpoints.get(102L).pinned()).isFalse();
        assertThat(checkpoints.get(102L).sessionId()).isEqualTo(9L);
        verify(sessionStore, never()).findByOrganizationIdAndId(any(), any());
    }

    @Test
    void resolveAllNeverAsksForTheSoonestSessionForAPageOfSettledUnpinnedItems() {

        var settledItems = List.of(
                ActionItemRecord.builder().id(101L).organizationId("default").communityId(1L)
                        .outcome(ActionItemOutcome.DONE).build(),
                ActionItemRecord.builder().id(102L).organizationId("default").communityId(1L)
                        .outcome(ActionItemOutcome.DROPPED).build());

        var checkpoints = resolver.resolveAll("default", 1L, settledItems);

        assertThat(checkpoints.get(101L).sessionId()).isNull();
        assertThat(checkpoints.get(102L).sessionId()).isNull();
        verify(sessionStore, never()).findSoonestUpcoming(any(), any(), any());
    }

    @Test
    void resolveAllOfAnAllPinnedPageNeverAsksForTheSoonestSession() {

        var pinnedSession = SessionRecordMother.any().id(5L).communityId(1L)
                .scheduledAt(Instant.parse("2020-01-01T10:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndCommunityIdAndIdIn("default", 1L, List.of(5L)))
                .willReturn(List.of(pinnedSession));

        var checkpoints = resolver.resolveAll("default", 1L, List.of(item(5L)));

        assertThat(checkpoints).hasSize(1);
        verify(sessionStore, never()).findSoonestUpcoming(any(), any(), any());
    }

    @Test
    void resolveAllOfAnEmptyPageFiresNoQueryAtAll() {

        var checkpoints = resolver.resolveAll("default", 1L, List.of());

        assertThat(checkpoints).isEmpty();
        verify(sessionStore, never()).findByOrganizationIdAndCommunityIdAndIdIn(any(), any(), any());
        verify(sessionStore, never()).findSoonestUpcoming(any(), any(), any());
    }

    @Test
    void resolveAllOfAPinnedSessionThatCannotBeFoundIsAnIllegalState() {

        given(sessionStore.findByOrganizationIdAndCommunityIdAndIdIn("default", 1L, List.of(5L))).willReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveAll("default", 1L, List.of(item(5L))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("5");
    }

    /**
     * The zone half of this resolver, proven for each of the two columns it can come from and for their absence. A
     * checkpoint date is rendered as a calendar day, so a checkpoint carrying no zone is read on the viewer's clock and
     * a Paris session names a different day in Tokyo than the session card behind it does.
     */
    @Test
    void anOccurrenceOfASeriesCarriesTheSeriesZone() {

        // given
        var occurrence = SessionRecordMother.any().id(5L).communityId(1L).seriesId(3L).timeZone(null)
                .scheduledAt(Instant.parse("2026-10-01T22:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndId("default", 5L)).willReturn(Optional.of(occurrence));
        given(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(3L)))
                .willReturn(List.of(SeriesRecordMother.any().id(3L).timeZone("Europe/Paris").build()));

        // when
        var actual = resolver.resolve("default", item(5L));

        // then
        assertThat(actual.governingTimeZone()).isEqualTo("Europe/Paris");
    }

    // The stored zone is the creator's own, so honouring it would make the chip contradict the card and the picker.
    @Test
    void aOneOffCarriesNoZoneEvenWhenOneIsStored() {

        // given
        var oneOff = SessionRecordMother.any().id(5L).communityId(1L).seriesId(null).timeZone("Asia/Tokyo")
                .scheduledAt(Instant.parse("2026-10-01T22:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndId("default", 5L)).willReturn(Optional.of(oneOff));

        // when
        var actual = resolver.resolve("default", item(5L));

        // then
        assertThat(actual.governingTimeZone()).isNull();
        verify(seriesStore, never()).findByOrganizationIdAndIdIn(any(), any());
    }

    @Test
    void aSessionWithNeitherZoneCarriesNull() {

        // given
        var zoneless = SessionRecordMother.any().id(5L).communityId(1L).seriesId(null).timeZone(null)
                .scheduledAt(Instant.parse("2026-10-01T22:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndId("default", 5L)).willReturn(Optional.of(zoneless));

        // when
        var actual = resolver.resolve("default", item(5L));

        // then
        assertThat(actual.governingTimeZone()).isNull();
    }

    @Test
    void aLateBoundCheckpointCarriesTheZoneOfTheSessionItResolvedTo() {

        // given
        var upcoming = SessionRecordMother.any().id(9L).communityId(1L).seriesId(7L).timeZone(null)
                .scheduledAt(Instant.parse("2026-10-01T22:00:00Z")).build();
        given(sessionStore.findSoonestUpcoming("default", 1L, NOW)).willReturn(Optional.of(upcoming));
        given(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(7L)))
                .willReturn(List.of(SeriesRecordMother.any().id(7L).timeZone("Asia/Tokyo").build()));

        // when
        var actual = resolver.resolve("default", item(null));

        // then
        assertThat(actual.governingTimeZone()).isEqualTo("Asia/Tokyo");
    }

    /**
     * A page whose pinned item and late-bound item land on sessions of two different series: each row carries its own
     * session's zone, and the whole page still costs one series query rather than one per row.
     */
    @Test
    void resolveAllCarriesEachItemsOwnZoneInOneSeriesQuery() {

        // given
        var pinnedSession = SessionRecordMother.any().id(5L).communityId(1L).seriesId(3L).timeZone(null)
                .scheduledAt(Instant.parse("2026-10-08T16:00:00Z")).build();
        var soonest = SessionRecordMother.any().id(9L).communityId(1L).seriesId(7L).timeZone(null)
                .scheduledAt(Instant.parse("2026-10-01T22:00:00Z")).build();
        given(sessionStore.findByOrganizationIdAndCommunityIdAndIdIn("default", 1L, List.of(5L)))
                .willReturn(List.of(pinnedSession));
        given(sessionStore.findSoonestUpcoming("default", 1L, NOW)).willReturn(Optional.of(soonest));
        given(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(3L, 7L)))
                .willReturn(List.of(SeriesRecordMother.any().id(3L).timeZone("Europe/Paris").build(),
                        SeriesRecordMother.any().id(7L).timeZone("Asia/Tokyo").build()));
        var pinnedItem = ActionItemRecord.builder().id(101L).organizationId("default").communityId(1L)
                .checkpointSessionId(5L).outcome(ActionItemOutcome.OPEN).build();
        var unpinnedItem = ActionItemRecord.builder().id(102L).organizationId("default").communityId(1L)
                .outcome(ActionItemOutcome.OPEN).build();

        // when
        var actual = resolver.resolveAll("default", 1L, List.of(pinnedItem, unpinnedItem));

        // then
        assertThat(actual.get(101L).governingTimeZone()).isEqualTo("Europe/Paris");
        assertThat(actual.get(102L).governingTimeZone()).isEqualTo("Asia/Tokyo");
        verify(seriesStore, times(1)).findByOrganizationIdAndIdIn(any(), any());
    }

    @Test
    void resolveAllOfAnEmptyPageAsksForNoSeriesZoneEither() {

        // when
        var actual = resolver.resolveAll("default", 1L, List.of());

        // then
        assertThat(actual).isEmpty();
        verify(seriesStore, never()).findByOrganizationIdAndIdIn(any(), any());
    }
}
