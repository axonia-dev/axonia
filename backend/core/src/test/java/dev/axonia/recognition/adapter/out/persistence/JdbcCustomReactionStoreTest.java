package dev.axonia.recognition.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.recognition.domain.CustomReaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcCustomReactionStoreTest {

    private static final String ORG = "org-1";

    private static final CustomReaction STORED = CustomReaction.builder().id(1L).organizationId(ORG).name("ship-it")
            .imageKey("reactions/org-1/ship.png").state(CustomReaction.APPROVED).proposedByUserId("user-1")
            .approvedByUserId("user-2").createdAt(Instant.parse("2026-06-02T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-03T00:00:00Z")).version(3L).build();

    private static final CustomReaction PENDING = STORED.toBuilder().id(2L).name("yak-shaving")
            .state(CustomReaction.PENDING).approvedByUserId(null).build();

    @Mock
    private ReactionDefinitionRepository repository;

    @InjectMocks
    private JdbcCustomReactionStore sut;

    @Test
    void allMapsEveryColumn() {

        // given
        when(repository.findByOrganizationIdOrderByNameAsc(ORG)).thenReturn(
                List.of(JdbcCustomReactionStore.toEntity(STORED), JdbcCustomReactionStore.toEntity(PENDING)));

        // when / then
        assertThat(sut.all(ORG)).containsExactly(STORED, PENDING);
    }

    @Test
    void inStateReadsOneState() {

        // given
        when(repository.findByOrganizationIdAndStateOrderByNameAsc(ORG, CustomReaction.APPROVED))
                .thenReturn(List.of(JdbcCustomReactionStore.toEntity(STORED)));

        // when / then
        assertThat(sut.inState(ORG, CustomReaction.APPROVED)).containsExactly(STORED);
    }

    @Test
    void proposedByReadsOneProposersRows() {

        // given
        when(repository.findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(ORG, CustomReaction.PENDING,
                "user-1")).thenReturn(List.of(JdbcCustomReactionStore.toEntity(PENDING)));

        // when / then
        assertThat(sut.proposedBy(ORG, CustomReaction.PENDING, "user-1")).containsExactly(PENDING);
    }

    @Test
    void findLooksUpWithinTheOrganization() {

        // given
        when(repository.findByOrganizationIdAndId(ORG, 1L))
                .thenReturn(Optional.of(JdbcCustomReactionStore.toEntity(STORED)));

        // when / then
        assertThat(sut.find(ORG, 1L)).contains(STORED);
    }

    @Test
    void nameTakenAsksTheRepository() {

        // given
        when(repository.existsByOrganizationIdAndName(ORG, "ship-it")).thenReturn(true);

        // when / then
        assertThat(sut.nameTaken(ORG, "ship-it")).isTrue();
    }

    @Test
    void countsAskTheRepository() {

        // given
        when(repository.countByOrganizationIdAndState(ORG, CustomReaction.APPROVED)).thenReturn(60);
        when(repository.countByOrganizationIdAndStateAndProposedByUserId(ORG, CustomReaction.PENDING, "user-1"))
                .thenReturn(3);

        // when / then
        assertThat(sut.countInState(ORG, CustomReaction.APPROVED)).isEqualTo(60);
        assertThat(sut.countProposedBy(ORG, CustomReaction.PENDING, "user-1")).isEqualTo(3);
    }

    @Test
    void usageByDefinitionIndexesTheJoinQuery() {

        // given
        when(repository.countUsageByDefinition(ORG))
                .thenReturn(List.of(new ReactionDefinitionRepository.DefinitionUsage(9L, 48L),
                        new ReactionDefinitionRepository.DefinitionUsage(10L, 0L)));

        // when / then
        assertThat(sut.usageByDefinition(ORG)).isEqualTo(Map.of(9L, 48L, 10L, 0L));
    }

    @Test
    void saveWritesEveryColumnAndAnswersWhatWasStored() {

        // given
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var saved = ArgumentCaptor.forClass(ReactionDefinitionEntity.class);

        // when
        var actual = sut.save(STORED);

        // then
        verify(repository).save(saved.capture());
        assertThat(JdbcCustomReactionStore.toCustomReaction(saved.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    @Test
    void removeDeletesTheStoredRowVersionIncluded() {

        // given
        var deleted = ArgumentCaptor.forClass(ReactionDefinitionEntity.class);

        // when
        sut.remove(STORED);

        // then
        verify(repository).delete(deleted.capture());
        assertThat(JdbcCustomReactionStore.toCustomReaction(deleted.getValue())).isEqualTo(STORED);
    }
}
