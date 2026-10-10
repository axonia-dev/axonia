package dev.axonia.events.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicVoteRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private TopicVoteRepository topicVoteRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 1,'default','c1','C1', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
        topicRepository.save(TopicEntityMother.any().build());
    }

    @Test
    void countByOrganizationIdAndTopicId() {

        // given
        var topicId = topicRepository.findAll().iterator().next().getId();
        topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("alice").build());
        topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("bob").build());

        // when
        var actual = topicVoteRepository.countByOrganizationIdAndTopicId("default", topicId);

        // then
        assertThat(actual).isEqualTo(2L);
    }

    @Test
    void save_duplicateVote_violatesUniqueConstraint() {

        // given
        var topicId = topicRepository.findAll().iterator().next().getId();
        topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("alice").build());

        // when / then
        assertThatThrownBy(
                () -> topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("alice").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOrganizationIdAndTopicIdAndUserId() {

        // given
        var topicId = topicRepository.findAll().iterator().next().getId();
        topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("alice").build());

        // when / then
        assertThat(topicVoteRepository.findByOrganizationIdAndTopicIdAndUserId("default", topicId, "alice"))
                .isPresent();
        assertThat(topicVoteRepository.findByOrganizationIdAndTopicIdAndUserId("default", topicId, "nobody")).isEmpty();
    }

    @Test
    void findByOrganizationIdAndTopicIdIn() {

        // given - two votes on the seeded topic
        var topicId = topicRepository.findAll().iterator().next().getId();
        topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("alice").build());
        topicVoteRepository.save(TopicVoteEntityMother.any().topicId(topicId).userId("bob").build());

        // when / then - every vote on the requested topics is returned in one read
        assertThat(topicVoteRepository.findByOrganizationIdAndTopicIdIn("default", List.of(topicId)))
                .extracting(TopicVoteEntity::getUserId).containsExactlyInAnyOrder("alice", "bob");
        assertThat(topicVoteRepository.findByOrganizationIdAndTopicIdIn("default", List.of(-1L))).isEmpty();
    }
}
