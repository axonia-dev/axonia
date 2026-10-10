package dev.axonia.recognition.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.domain.ReactionPlacement;
import dev.axonia.recognition.domain.ReactionPlacementKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcReactionStoreTest {

    private static final ReactionPlacement STORED = ReactionPlacement.builder().id(3L).organizationId("org-1")
            .communityId(7L).contributionType(ContributionType.SLOT_PRESENTED).contributionId(1L)
            .reactorUserId("user-1").definitionId(42L).createdAt(Instant.parse("2026-06-02T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-03T00:00:00Z")).version(1L).build();

    @Mock
    private ReactionRepository reactions;

    @InjectMocks
    private JdbcReactionStore sut;

    @Test
    void placementsOnMapsEveryColumn() {

        // given
        var emoji = STORED.toBuilder().id(4L).definitionId(null).emoji("🧠").build();
        when(reactions.findByOrganizationIdAndContributionTypeAndContributionId("org-1",
                ContributionType.SLOT_PRESENTED, 1L))
                .thenReturn(List.of(JdbcReactionStore.toEntity(STORED), JdbcReactionStore.toEntity(emoji)));

        // when
        var actual = sut.placementsOn("org-1", ContributionType.SLOT_PRESENTED, 1L);

        // then
        assertThat(actual).containsExactly(STORED, emoji);
    }

    @Test
    void placeStoresTheReaction() {

        // given
        var placement = STORED.toBuilder().id(null).createdAt(null).updatedAt(null).version(null).build();
        var saved = ArgumentCaptor.forClass(ReactionEntity.class);

        // when
        var actual = sut.place(placement);

        // then
        assertThat(actual).isTrue();
        verify(reactions).save(saved.capture());
        assertThat(JdbcReactionStore.toPlacement(saved.getValue())).isEqualTo(placement);
    }

    @Test
    void placingTheSameReactionTwiceIsRefused() {

        // given
        when(reactions.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        // when / then
        assertThat(sut.place(STORED)).isFalse();
    }

    @Test
    void removeAnswersHowManyPlacementsWereRemoved() {

        // given
        var key = ReactionPlacementKey.builder().organizationId("org-1")
                .contributionType(ContributionType.SLOT_PRESENTED).contributionId(1L).reactorUserId("user-1")
                .emoji("🧠").build();
        when(reactions.deletePlacement(key)).thenReturn(1);

        // when / then
        assertThat(sut.remove(key)).isEqualTo(1);
    }
}
