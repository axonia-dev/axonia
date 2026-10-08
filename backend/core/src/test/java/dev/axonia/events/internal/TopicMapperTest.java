package dev.axonia.events.internal;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.events.mother.TopicEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

class TopicMapperTest {

    private final TopicMapper sut = new TopicMapper();

    @Test
    void mapsTagsSorted() {

        // given
        var entity = TopicEntityMother.any()
                .tags(Set.of(TopicTagRef.builder().tag("b").build(), TopicTagRef.builder().tag("a").build())).build();

        // when
        var tags = sut.tags(entity);

        // then
        assertThat(tags).containsExactly("a", "b");
    }

    @Test
    void nullTagSetMapsToEmptyList() {

        // given
        var entity = TopicEntityMother.any().tags(null).build();

        // when
        var tags = sut.tags(entity);

        // then
        assertThat(tags).isEmpty();
    }

    @Test
    void duplicateTagsDeduplicatedAndSorted() {

        // given
        var entity = TopicEntityMother.any()
                .tags(Set.of(TopicTagRef.builder().tag("kafka").build(), TopicTagRef.builder().tag("Avro").build()))
                .build();

        // when
        var tags = sut.tags(entity);

        // then
        assertThat(tags).containsExactly("Avro", "kafka");
        assertThat(tags).doesNotHaveDuplicates();
    }
}
