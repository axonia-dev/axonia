package dev.axonia.events.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V128 runs against topics seeded while {@code topic.kind} still exists: a backlog offer keeps its proposer as its
 * speaker unless that proposer is an erasure surrogate, and nothing else gains a row, since a scheduled or done topic
 * reads its speakers through its slot.
 */
class TopicSpeakerMigrationTest {

    private static DedicatedPostgresContainer pg;
    private static JdbcTemplate jdbc;

    private static long backlogOffer;
    private static long backlogRequest;
    private static long scheduledOffer;
    private static long doneOffer;
    private static long erasedOffer;

    @BeforeAll
    static void migrateAroundLegacyTopics() {

        pg = new DedicatedPostgresContainer();
        pg.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        MigrationChain.SELF_HOSTED.upTo(pg, "127").migrate();
        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0)");
        var communityId = jdbc.queryForObject("INSERT INTO communities (organization_id, slug, name, kind_id, "
                + "description, status, created_at, updated_at, version) SELECT 'default', 'guild', 'Guild', k.id, '', "
                + "'ACTIVE', now(), now(), 0 FROM community_kind k RETURNING id", Long.class);
        backlogOffer = legacyTopic(communityId, "OFFER", "PROPOSED", "alice");
        backlogRequest = legacyTopic(communityId, "REQUEST", "PROPOSED", "bob");
        scheduledOffer = legacyTopic(communityId, "OFFER", "SCHEDULED", "carol");
        doneOffer = legacyTopic(communityId, "OFFER", "DONE", "dave");
        jdbc.update("INSERT INTO erasure_receipt (organization_id, surrogate_id, erased_at, deleted_row_count, "
                + "cleared_row_count, anonymised_row_count, media_residue) VALUES ('default', 'erased-1', now(), 0, 0, "
                + "0, 0)");
        erasedOffer = legacyTopic(communityId, "OFFER", "PROPOSED", "erased-1");
        MigrationChain.SELF_HOSTED.on(pg).migrate();
    }

    @AfterAll
    static void stop() {

        pg.stop();
    }

    @Test
    void aBacklogOfferKeepsItsProposerAsItsSpeaker() {

        assertThat(speakersOf(backlogOffer)).containsExactly("alice");
    }

    @Test
    void noOtherTopicGainsASpeakerRow() {

        assertThat(speakersOf(backlogRequest)).isEmpty();
        assertThat(speakersOf(scheduledOffer)).isEmpty();
        assertThat(speakersOf(doneOffer)).isEmpty();
    }

    @Test
    void aBacklogOfferOfAnErasedCreatorGainsNoSpeaker() {

        assertThat(speakersOf(erasedOffer)).isEmpty();
    }

    @Test
    void theKindColumnIsGone() {

        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'topic' "
                + "AND column_name = 'kind'", Long.class)).isZero();
    }

    private static long legacyTopic(long communityId, String kind, String status, String proposer) {

        return jdbc.queryForObject("INSERT INTO topic (organization_id, community_id, kind, title, description, "
                + "proposer_user_id, status, created_at, updated_at, version) VALUES ('default', ?, ?, 'T', '', ?, ?, "
                + "now(), now(), 0) RETURNING id", Long.class, communityId, kind, proposer, status);
    }

    private static List<String> speakersOf(long topicId) {

        return jdbc.queryForList("SELECT user_id FROM topic_speaker WHERE topic_id = ?", String.class, topicId);
    }
}
