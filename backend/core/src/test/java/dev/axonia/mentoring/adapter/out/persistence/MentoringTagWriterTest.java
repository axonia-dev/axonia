package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Import({JournalRepositoryImpl.class, PairingRepositoryImpl.class, WordsRepositoryImpl.class})
class MentoringTagWriterTest extends MentoringRepositoryTestSupport {

    @Autowired
    private JournalRepositoryImpl journal;

    @Autowired
    private PairingRepositoryImpl pairings;

    private MentoringTagWriter sut;

    private long platform;
    private long data;
    private long inPlatform;
    private long inData;

    @BeforeEach
    void setUp() {

        sut = new MentoringTagWriter(() -> ORG, journal);
        platform = seedCommunity("platform");
        data = seedCommunity("data");
        inPlatform = pairings.insert(ORG, pairing(platform, "mia", "max").build());
        inData = pairings.insert(ORG, pairing(data, "mia", "max").build());
    }

    private void topic(long pairingId, String label, String area, int position) {

        journal.insertTopic(ORG, Topic.builder().pairingId(pairingId).label(label).area(area).state(TopicState.TODO)
                .position(position).createdAt(Instant.parse("2026-10-01T09:00:00Z")).build());
    }

    @Test
    void aPrivateJournalIsNeverASourceOfATag() {

        // given
        topic(inPlatform, "Kafka", "kafka", 0);
        topic(inPlatform, "Feedback", null, 1);
        topic(inData, "Spark", "spark", 0);

        // when
        var actual = sut.tagsIn(platform);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void renameMovesTheAreaInThisGuildOnly() {

        // given
        topic(inPlatform, "Containers", "k8s", 0);
        topic(inData, "Containers", "k8s", 0);

        // when
        sut.rename(platform, "k8s", "kubernetes");

        // then
        assertThat(journal.topicsOf(ORG, inPlatform)).extracting(Topic::label, Topic::area)
                .containsExactly(tuple("Containers", "kubernetes"));
        assertThat(journal.topicsOf(ORG, inData)).extracting(Topic::area).containsExactly("k8s");
    }

    @Test
    void removeUnlinksTheTopicsAndKeepsTheirLabels() {

        // given
        topic(inPlatform, "Kafka streams", "kafka", 0);
        topic(inData, "Kafka", "kafka", 0);

        // when
        sut.remove(platform, "kafka");

        // then
        assertThat(journal.topicsOf(ORG, inPlatform)).extracting(Topic::label, Topic::area)
                .containsExactly(tuple("Kafka streams", null));
        assertThat(journal.topicsOf(ORG, inData)).extracting(Topic::area).containsExactly("kafka");
    }

    @Test
    void usageReportsNothingBecauseRemovingLosesNothing() {

        // given
        topic(inPlatform, "Kafka", "kafka", 0);

        // when
        var actual = sut.usage(platform, "kafka");

        // then
        assertThat(actual).isEmpty();
    }
}
