package dev.axonia.events.api;

import java.util.List;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TopicMother {

    public Topic.TopicBuilder any() {

        return Topic.builder().id(1L).communityId(1L).kind(TopicKind.REQUEST).title("Intro to event sourcing")
                .description("Would love a session").tags(List.of("kafka")).proposerDisplayName("alice").voteCount(0L)
                .status(TopicStatus.PROPOSED);
    }
}
