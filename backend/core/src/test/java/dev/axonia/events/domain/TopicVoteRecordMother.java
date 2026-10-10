package dev.axonia.events.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TopicVoteRecordMother {

    public TopicVoteRecord.TopicVoteRecordBuilder any() {

        return TopicVoteRecord.builder().organizationId("default").topicId(1L).userId("alice");
    }
}
