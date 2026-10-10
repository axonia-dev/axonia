package dev.axonia.community.adapter.out.persistence;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatDestinationRepositoryTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";

    @Autowired
    private ChatDestinationRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private Long communityId;

    /**
     * A plain {@code communities} row via raw JDBC, not a {@link CommunityEntity} save: the FK this test needs is on
     * the row, not on {@code CommunityEntity}'s shape, and coupling to the entity would make this test collateral
     * damage of any change to that entity.
     */
    @BeforeEach
    void seedCommunity() {

        var kindId = jdbc
                .queryForObject(
                        "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                                + "VALUES (:org, 'Guild', now(), now(), 0) RETURNING id",
                        Map.of("org", ORG), Long.class);
        communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'backend-guild', 'Backend Guild', :kindId, '', "
                        + "'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "kindId", kindId), Long.class);
    }

    private ChatDestinationEntity destination(String name) {

        return ChatDestinationEntity.builder().organizationId(ORG).communityId(communityId).name(name)
                .webhookUrl("https://hooks.slack.com/services/x").build();
    }

    @Test
    void findsACommunitysDestinationsOldestFirst() {

        var first = repository.save(destination("#platform-guild"));
        var second = repository.save(destination("#architecture"));

        assertThat(repository.findByOrganizationIdAndCommunityIdOrderByIdAsc(ORG, communityId))
                .extracting(ChatDestinationEntity::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void refusesTwoDestinationsWithTheSameNameInOneCommunity() {

        repository.save(destination("#platform-guild"));

        assertThatThrownBy(() -> repository.save(destination("#platform-guild")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("community_chat_destination_named_once");
    }

    /**
     * {@code findIdsByOrganizationIdAndCommunityId} is the one hand-written {@code @Query} the whole chat expansion
     * path depends on, and every one of its callers mocks the layer beneath rather than exercising it against a real
     * database. A broken query here would fail silently and totally: every placeholder would retire
     * {@code NO_DESTINATION}, reading as a legitimate "no destinations configured" answer rather than a broken query,
     * and no chat message would post for any tenant until someone thought to check the SQL itself.
     */
    @Test
    void findsIdsForACommunityInIdOrderAndNothingForAnother() {

        var first = repository.save(destination("#platform-guild"));
        var second = repository.save(destination("#architecture"));
        var otherCommunityId = anotherCommunity();

        assertThat(repository.findIdsByOrganizationIdAndCommunityId(ORG, communityId)).containsExactly(first.getId(),
                second.getId());
        assertThat(repository.findIdsByOrganizationIdAndCommunityId(ORG, otherCommunityId)).isEmpty();
    }

    private Long anotherCommunity() {

        var kindId = jdbc
                .queryForObject(
                        "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                                + "VALUES (:org, 'Other kind', now(), now(), 0) RETURNING id",
                        Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'other-guild', 'Other Guild', :kindId, '', "
                        + "'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "kindId", kindId), Long.class);
    }
}
