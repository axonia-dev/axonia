package dev.axonia.events.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TopicVoteEntityMother {

    public TopicVoteEntity.TopicVoteEntityBuilder any() {

        return TopicVoteEntity.builder().organizationId("default").topicId(1L).userId("alice");
    }
}
