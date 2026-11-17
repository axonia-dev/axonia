package dev.axonia.mentoring.adapter.out.titles;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.events.api.NamedSession;
import dev.axonia.knowledge.api.ArtifactTitles;
import dev.axonia.knowledge.api.TitledArtifact;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerKind;
import dev.axonia.mentoring.domain.PointerTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointerTitlesAdapterTest {

    @Mock
    private ActivityTitles activityTitles;
    @Mock
    private ArtifactTitles artifactTitles;

    @InjectMocks
    private PointerTitlesAdapter sut;

    private static Pointer pointer(long id, PointerKind kind, Long targetId) {

        return Pointer.builder().id(id).pairingId(90L).topicId(1L).kind(kind).targetId(targetId).build();
    }

    @Test
    void eachKindIsResolvedByItsOwnerWithItsGuildAndATargetGoneIsAbsent() {

        // given
        var session = pointer(1L, PointerKind.SESSION, 42L);
        var sameSession = pointer(2L, PointerKind.SESSION, 42L);
        var artifact = pointer(4L, PointerKind.ARTIFACT, 70L);
        var deletedArtifact = pointer(5L, PointerKind.ARTIFACT, 71L);
        var link = Pointer.builder().id(8L).kind(PointerKind.LINK).url("https://example.test").build();
        when(activityTitles.sessionNames(List.of(42L))).thenReturn(Map.of(42L, new NamedSession(7L, "Kafka night")));
        when(artifactTitles.artifactsByIds(List.of(70L, 71L)))
                .thenReturn(Map.of(70L, new TitledArtifact(8L, "Schema registry notes")));

        // when
        var actual = sut.resolve(List.of(session, sameSession, artifact, deletedArtifact, link));

        // then
        assertThat(actual).containsOnly(entry(1L, new PointerTarget("Kafka night", 7L)),
                entry(2L, new PointerTarget("Kafka night", 7L)),
                entry(4L, new PointerTarget("Schema registry notes", 8L)));
    }

    @Test
    void linksAloneAskNobody() {

        // when
        var actual = sut.resolve(List.of(Pointer.builder().id(8L).kind(PointerKind.LINK).build()));

        // then
        assertThat(actual).isEmpty();
        verifyNoInteractions(activityTitles, artifactTitles);
    }
}
