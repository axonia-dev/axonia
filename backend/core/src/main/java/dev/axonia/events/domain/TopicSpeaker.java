package dev.axonia.events.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class TopicSpeaker {

    private String userId;
}
