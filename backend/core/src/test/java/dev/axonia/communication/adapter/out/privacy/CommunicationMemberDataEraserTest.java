package dev.axonia.communication.adapter.out.privacy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommunicationMemberDataEraser eraser;

    @Test
    void erasingAMemberRemovesTheirPreferencesAndTheirQueuedMailAndSparesTheNeighbour() {

        seedPreference(TARGET, "session_upcoming");
        seedPreference(TARGET, "session_wrap_up");
        seedDelivery(seedMessage("session_upcoming", "session:1"), TARGET);
        seedDelivery(seedMessage("session_wrap_up", "session:1"), TARGET);
        seedDelivery(seedMessage("session_awaiting_close", "session:1"), TARGET);
        seedPreference(NEIGHBOUR, "session_upcoming");
        seedDelivery(seedMessage("session_upcoming", "session:2"), NEIGHBOUR);

        var counts = eraser.erase(erasureOf(TARGET));

        assertThat(counts.deleted())
                .as("two preferences, three deliveries, and the three messages nobody is left to hold").isEqualTo(8);
        assertThat(countPreference(TARGET)).isZero();
        assertThat(countDelivery(TARGET)).isZero();
        assertThat(countPreference(NEIGHBOUR)).isEqualTo(1);
        assertThat(countDelivery(NEIGHBOUR)).isEqualTo(1);
        assertThat(countMessage()).isEqualTo(1);
    }

    @Test
    void erasingOneRecipientLeavesTheMessageForItsOtherRecipients() {

        var messageId = seedMessage("session_wrap_up", "session:shared");
        seedDelivery(messageId, TARGET);
        seedDelivery(messageId, NEIGHBOUR);

        eraser.erase(erasureOf(TARGET));

        assertThat(countDelivery(TARGET)).isZero();
        assertThat(countDelivery(NEIGHBOUR)).isEqualTo(1);
        assertThat(countMessage()).as("the neighbour has not been erased and this is still their message").isEqualTo(1);
    }

    @Test
    void erasingTheLastRecipientRemovesTheMessageToo() {

        seedDelivery(seedMessage("session_wrap_up", "session:sole"), TARGET);

        eraser.erase(erasureOf(TARGET));

        assertThat(countMessage()).as("nobody is left to hold this message").isZero();
    }

    private static MemberErasure erasureOf(String userId) {

        return MemberErasure.builder().organizationId(ORG).userId(userId).surrogateId("erased:test-surrogate")
                .erasedAt(Instant.parse("2026-07-31T10:00:00Z")).build();
    }

    private void seedPreference(String userId, String messageType) {

        jdbc.update(
                "INSERT INTO communication_preference (organization_id, user_id, message_type, enabled, "
                        + "created_at, updated_at, version) VALUES (:org, :user, :type, true, now(), now(), 0)",
                Map.of("org", ORG, "user", userId, "type", messageType));
    }

    private Long seedMessage(String messageType, String subjectRef) {

        return jdbc.queryForObject(
                "INSERT INTO communication_message (organization_id, message_type, subject_ref, template, facts, "
                        + "created_at, updated_at, version) VALUES (:org, :type, :subject, 'session-template', "
                        + "'{}'::jsonb, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "type", messageType, "subject", subjectRef), Long.class);
    }

    private void seedDelivery(Long messageId, String userId) {

        jdbc.update(
                "INSERT INTO communication_delivery (organization_id, message_id, user_id, status, created_at, "
                        + "updated_at, version) VALUES (:org, :message, :user, 'PENDING', now(), now(), 0)",
                Map.of("org", ORG, "message", messageId, "user", userId));
    }

    private long countPreference(String userId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM communication_preference WHERE organization_id = :org AND user_id = :user",
                Map.of("org", ORG, "user", userId), Long.class);
    }

    private long countDelivery(String userId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM communication_delivery WHERE organization_id = :org AND user_id = :user",
                Map.of("org", ORG, "user", userId), Long.class);
    }

    private long countMessage() {

        return jdbc.queryForObject("SELECT count(*) FROM communication_message WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }
}
