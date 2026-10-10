package dev.axonia.events.application;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.events.domain.TopicRecordMother;
import dev.axonia.events.domain.TopicTag;

import static org.assertj.core.api.Assertions.assertThat;

class TopicMapperTest {

    private final TopicMapper sut = new TopicMapper();

    @Test
    void mapsTagsSorted() {

        // given
        var entity = TopicRecordMother.any()
                .tags(Set.of(TopicTag.builder().tag("b").build(), TopicTag.builder().tag("a").build())).build();

        // when
        var tags = sut.tags(entity);

        // then
        assertThat(tags).containsExactly("a", "b");
    }

    @Test
    void nullTagSetMapsToEmptyList() {

        // given
        var entity = TopicRecordMother.any().tags(null).build();

        // when
        var tags = sut.tags(entity);

        // then
        assertThat(tags).isEmpty();
    }

    @Test
    void duplicateTagsDeduplicatedAndSorted() {

        // given
        var entity = TopicRecordMother.any()
                .tags(Set.of(TopicTag.builder().tag("kafka").build(), TopicTag.builder().tag("Avro").build())).build();

        // when
        var tags = sut.tags(entity);

        // then
        assertThat(tags).containsExactly("Avro", "kafka");
        assertThat(tags).doesNotHaveDuplicates();
    }
}
