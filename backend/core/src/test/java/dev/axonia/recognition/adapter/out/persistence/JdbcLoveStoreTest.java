package dev.axonia.recognition.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.recognition.domain.GivenLove;
import dev.axonia.recognition.domain.GivenLoveMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcLoveStoreTest {

    private static final GivenLove STORED = GivenLoveMother.any().id(9L).recognizedContributionId(5L)
            .createdAt(Instant.parse("2026-06-02T00:00:00Z")).updatedAt(Instant.parse("2026-06-03T00:00:00Z"))
            .version(1L).build();

    @Mock
    private LoveRepository loveRepository;

    @InjectMocks
    private JdbcLoveStore sut;

    @Test
    void findMapsEveryColumn() {

        // given
        when(loveRepository.findByOrganizationIdAndRecognizedContributionIdAndLoverUserId("default", 5L, "bob"))
                .thenReturn(Optional.of(JdbcLoveStore.toEntity(STORED)));

        // when
        var actual = sut.find("default", 5L, "bob");

        // then
        assertThat(actual).contains(STORED);
    }

    @Test
    void ofRecognitionListsEveryLove() {

        // given
        var other = GivenLoveMother.any().id(10L).recognizedContributionId(5L).loverUserId("carol").build();
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L))
                .thenReturn(List.of(JdbcLoveStore.toEntity(STORED), JdbcLoveStore.toEntity(other)));

        // when
        var actual = sut.ofRecognition("default", 5L);

        // then
        assertThat(actual).containsExactly(STORED, other);
    }

    @Test
    void addStoresTheLoveAndAnswersWhatWasStored() {

        // given
        var love = GivenLoveMother.any().recognizedContributionId(5L).build();
        when(loveRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0, LoveEntity.class).toBuilder().id(9L).build());
        var saved = ArgumentCaptor.forClass(LoveEntity.class);

        // when
        var actual = sut.add(love);

        // then
        verify(loveRepository).save(saved.capture());
        assertThat(JdbcLoveStore.toGivenLove(saved.getValue())).isEqualTo(love);
        assertThat(actual).contains(love.toBuilder().id(9L).build());
    }

    @Test
    void addOfALoveAlreadyGivenIsEmpty() {

        // given
        when(loveRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        // when / then
        assertThat(sut.add(GivenLoveMother.any().build())).isEmpty();
    }

    @Test
    void removeDeletesTheStoredRowVersionIncluded() {

        // given
        var deleted = ArgumentCaptor.forClass(LoveEntity.class);

        // when
        sut.remove(STORED);

        // then
        verify(loveRepository).delete(deleted.capture());
        assertThat(JdbcLoveStore.toGivenLove(deleted.getValue())).isEqualTo(STORED);
    }
}
