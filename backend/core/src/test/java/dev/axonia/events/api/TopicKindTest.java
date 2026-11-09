package dev.axonia.events.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicKindTest {

    @Test
    void forSpeakers_noSpeakerIsARequest() {

        assertThat(TopicKind.forSpeakers(List.of())).isEqualTo(TopicKind.REQUEST);
    }

    @Test
    void forSpeakers_anySpeakerIsAnOffer() {

        assertThat(TopicKind.forSpeakers(List.of("u"))).isEqualTo(TopicKind.OFFER);
    }
}
