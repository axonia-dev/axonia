package dev.axonia.learning.adapter.out.privacy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class LearningMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private LearningMemberDataEraser eraser;

    @Test
    void erasingAMemberDeletesTheirIntentsInEveryCommunityAndSparesAnotherMember() {

        // given
        seedIntent(ORG, 1L, ALICE, "kafka");
        seedIntent(ORG, 2L, ALICE, "rust");
        seedIntent(ORG, 1L, BOB, "kafka");

        // when
        var counts = eraser.erase(erasureOf(ALICE));

        // then
        assertThat(counts).isEqualTo(new ErasureCounts(2, 0, 0, 0));
        assertThat(count(ORG, ALICE)).isZero();
        assertThat(count(ORG, BOB)).isEqualTo(1);
    }

    @Test
    void leavesTheSameMembersIntentsInAnotherOrganizationUntouched() {

        // given
        seedIntent(OTHER_ORG, 1L, ALICE, "kafka");

        // when
        var counts = eraser.erase(erasureOf(ALICE));

        // then
        assertThat(counts).isEqualTo(new ErasureCounts(0, 0, 0, 0));
        assertThat(count(OTHER_ORG, ALICE)).isEqualTo(1);
    }

    @Test
    void erasingADeclarerForgetsTheExpiredNoticesAboutThem() {

        // given - two expired notices about alice, one about bob, one of another type ending like alice's
        seedExpired(ORG, "open_to_questions", "7:kafka:" + ALICE);
        seedExpired(ORG, "open_to_questions", "8:rust:" + ALICE);
        seedExpired(ORG, "open_to_questions", "7:kafka:" + BOB);
        seedExpired(ORG, "open_to_questions", "7:kafka:x" + ALICE);
        seedExpired(ORG, "session_wrap_up", "7:kafka:" + ALICE);
        seedExpired(OTHER_ORG, "open_to_questions", "7:kafka:" + ALICE);

        // when
        var counts = eraser.erase(erasureOf(ALICE));

        // then
        assertThat(counts).isEqualTo(new ErasureCounts(2, 0, 0, 0));
        assertThat(expiredSubjects(ORG)).containsExactlyInAnyOrder("open_to_questions 7:kafka:" + BOB,
                "open_to_questions 7:kafka:x" + ALICE, "session_wrap_up 7:kafka:" + ALICE);
        assertThat(expiredSubjects(OTHER_ORG)).containsExactly("open_to_questions 7:kafka:" + ALICE);
    }

    private void seedExpired(String org, String type, String subjectRef) {

        jdbc.update("INSERT INTO communication_expired_message (organization_id, message_type, subject_ref, "
                + "pending_count, sent_count, suppressed_count, unreachable_count, abandoned_count, expired_at) "
                + "VALUES (:org, :type, :subject, 0, 1, 0, 0, 0, now())",
                Map.of("org", org, "type", type, "subject", subjectRef));
    }

    private java.util.List<String> expiredSubjects(String org) {

        return jdbc.queryForList("SELECT message_type || ' ' || subject_ref FROM communication_expired_message "
                + "WHERE organization_id = :org", Map.of("org", org), String.class);
    }

    private static MemberErasure erasureOf(String userId) {

        return MemberErasure.builder().organizationId(ORG).userId(userId).surrogateId("erased:test-surrogate")
                .erasedAt(Instant.parse("2026-09-25T10:00:00Z")).build();
    }

    private void seedIntent(String org, long communityId, String userId, String tag) {

        jdbc.update(
                "INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                        + "VALUES (:org, :communityId, :user, :tag, now())",
                Map.of("org", org, "communityId", communityId, "user", userId, "tag", tag));
    }

    private long count(String org, String userId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM learning_intent WHERE organization_id = :org AND user_id = :user",
                Map.of("org", org, "user", userId), Long.class);
    }
}
