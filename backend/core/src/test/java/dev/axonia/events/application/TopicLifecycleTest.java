package dev.axonia.events.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.api.TopicTagsChanged;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicTag;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class TopicLifecycleTest {

    @Mock
    private TopicStore topicStore;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TopicLifecycle sut;

    @BeforeEach
    void wire() {

        sut = new TopicLifecycle(topicStore, new TopicEvidence(eventPublisher), (org, ids) -> Set.copyOf(ids));
    }

    @Test
    void schedule_handsTheSpeakersOverAndClearsThem() {

        when(topicStore.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(topic(TopicStatus.PROPOSED).withSpeakers(List.of("alice", "bob"))));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.schedule("default", 1L, 99L);

        assertThat(actual.speakerUserIds()).containsExactlyInAnyOrder("alice", "bob");
        assertThat(actual.topic().getStatus()).isEqualTo(TopicStatus.SCHEDULED);
        assertThat(actual.topic().speakerUserIds()).isEmpty();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void schedule_unknownTopicIsNotFound() {

        when(topicStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.schedule("default", 1L, 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void schedule_topicOfAnotherCommunityIsRefused() {

        when(topicStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(topic(TopicStatus.PROPOSED)));

        assertThatThrownBy(() -> sut.schedule("default", 2L, 99L)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("TOPIC_COMMUNITY_MISMATCH");
    }

    @ParameterizedTest
    @EnumSource(value = TopicStatus.class, names = {"SCHEDULED", "DONE"})
    void schedule_aTopicOffTheBacklogIsRefused(TopicStatus status) {

        when(topicStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(topic(status)));

        assertThatThrownBy(() -> sut.schedule("default", 1L, 99L)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("NOT_PROPOSED");
        verify(topicStore, never()).save(any());
    }

    @Test
    void release_returnsTheTopicWithTheSlotsPresentersAndRepublishesItsEvidence() {

        when(topicStore.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(topic(TopicStatus.SCHEDULED)));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.release("default", slot(99L, "carol", "bob"));

        verify(topicStore).save(argThat(
                t -> t.getStatus() == TopicStatus.PROPOSED && t.speakerUserIds().equals(List.of("bob", "carol"))));
        verify(eventPublisher).publishEvent(new TopicTagsChanged(99L, 1L, List.of("bob", "carol"), List.of("kafka")));
    }

    @Test
    void release_keepsErasedAndDeprovisionedPresentersOffTheBacklog() {

        sut = new TopicLifecycle(topicStore, new TopicEvidence(eventPublisher),
                (org, ids) -> "default".equals(org) && ids.contains("bob") ? Set.of("bob") : Set.of());
        when(topicStore.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(topic(TopicStatus.SCHEDULED)));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.release("default", slot(99L, "erased:alice", "deprovisioned-dave", "bob"));

        verify(topicStore).save(argThat(t -> t.speakerUserIds().equals(List.of("bob"))));
        verify(eventPublisher).publishEvent(new TopicTagsChanged(99L, 1L, List.of("bob"), List.of("kafka")));
    }

    @Test
    void releaseAll_returnsATopicOnTwoSlotsOnceWithThePresentersOfBoth() {

        when(topicStore.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(topic(TopicStatus.SCHEDULED)));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.releaseAll("default", List.of(slot(99L, "bob"), slot(null, "dave"), slot(99L, "carol", "bob")));

        verify(topicStore).save(argThat(t -> t.speakerUserIds().equals(List.of("bob", "carol"))));
        verify(eventPublisher).publishEvent(new TopicTagsChanged(99L, 1L, List.of("bob", "carol"), List.of("kafka")));
    }

    @Test
    void release_leavesADoneTopicAlone() {

        when(topicStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(topic(TopicStatus.DONE)));

        sut.release("default", slot(99L, "bob"));

        verify(topicStore, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void release_ofASlotWithoutATopicDoesNothing() {

        sut.release("default", slot(null, "bob"));

        verifyNoInteractions(topicStore, eventPublisher);
    }

    @Test
    void releaseReplaced_keepingTheSameTopicDoesNothing() {

        sut.releaseReplaced("default", slot(99L, "bob"), 99L);

        verifyNoInteractions(topicStore, eventPublisher);
    }

    @Test
    void releaseReplaced_swappingTheTopicReleasesTheOldOneWithItsPresenters() {

        when(topicStore.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(topic(TopicStatus.SCHEDULED)));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.releaseReplaced("default", slot(99L, "bob"), 100L);

        verify(topicStore).save(argThat(t -> t.getId().equals(99L) && t.speakerUserIds().equals(List.of("bob"))));
    }

    private static TopicRecord topic(TopicStatus status) {

        return TopicRecord.builder().id(99L).organizationId("default").communityId(1L).status(status)
                .tags(Set.of(TopicTag.builder().tag("kafka").build())).build();
    }

    private static SlotRecord slot(Long topicId, String... presenters) {

        return SlotRecord.builder().id(11L).topicId(topicId).presenters(Set.of(presenters).stream()
                .map(p -> SlotPresenter.builder().userId(p).build()).collect(Collectors.toSet())).build();
    }
}
