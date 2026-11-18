package dev.axonia.mentoring.adapter.out.privacy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "mentoring-erasure-org";
    private static final String OTHER_ORG = "mentoring-erasure-other-org";
    private static final String MENTOR = "mia";
    private static final String MENTEE = "noah";
    private static final String STRANGER = "leo";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private MentoringMemberDataEraser eraser;

    private MentoringRows rows;
    private long guild;

    @BeforeEach
    void seed() {

        rows = new MentoringRows(jdbc, ORG);
        guild = rows.community("Data Guild");
    }

    @Test
    void erasingTheMenteeKeepsTheMentorsNotesAndTopics() {

        // given
        var pairing = rows.pairing(guild, MENTOR, MENTEE);
        rows.topic(pairing, "Kafka");
        rows.note(pairing, MENTOR, "Mentor note");
        rows.note(pairing, MENTEE, "Mentee note");
        rows.words(pairing, "Patient and clear");

        // when
        var actual = eraser.erase(erasureOf(MENTEE));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(2, 1, 0, 0));
        assertThat(sides(pairing)).containsExactly(MENTOR, null);
        assertThat(strings("SELECT label FROM mentoring_topic WHERE organization_id = :org")).containsExactly("Kafka");
        assertThat(strings("SELECT body FROM mentoring_note WHERE organization_id = :org"))
                .containsExactly("Mentor note");
        assertThat(rows.count("mentoring_words")).isZero();
    }

    @Test
    void erasingTheMentorDeletesTheWordsWrittenToThem() {

        // given
        var pairing = rows.pairing(guild, MENTOR, MENTEE);
        rows.note(pairing, MENTEE, "Mentee note");
        rows.words(pairing, "Patient and clear");

        // when
        var actual = eraser.erase(erasureOf(MENTOR));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(1, 1, 0, 0));
        assertThat(sides(pairing)).containsExactly(null, MENTEE);
        assertThat(rows.count("mentoring_words")).isZero();
        assertThat(rows.count("mentoring_note")).isEqualTo(1);
    }

    @Test
    void erasureForgetsTheErasedSidesAnswerToShowingAndKeepsThePartners() {

        // given
        var asMentee = rows.pairing(guild, MENTOR, MENTEE);
        var asMentor = rows.pairing(guild, MENTEE, STRANGER);
        jdbc.update("UPDATE mentoring_pairing SET visibility = 'ASKING', mentor_said_yes = true, "
                + "mentee_said_yes = true WHERE organization_id = :org", Map.of("org", ORG));

        // when
        eraser.erase(erasureOf(MENTEE));

        // then
        assertThat(answers(asMentee)).containsExactly(true, false);
        assertThat(answers(asMentor)).containsExactly(false, true);
    }

    @Test
    void erasureRemovesTheUpcomingMeetingsTheyPlannedAndClearsThePlannerElsewhere() {

        // given: erased late on the 17th in UTC, already the 18th in Auckland
        var pairing = rows.pairing(guild, MENTOR, MENTEE);
        var upcoming = rows.meeting(pairing, MENTEE, "2026-11-26");
        var today = rows.meeting(pairing, MENTEE, "2026-11-17");
        var passed = rows.meeting(pairing, MENTEE, "2026-11-16");
        var theMentors = rows.meeting(pairing, MENTOR, "2026-11-27");
        var another = rows.pairing(guild, STRANGER, MENTEE);
        var heldToday = rows.meeting(another, MENTEE, "2026-11-17");
        rows.noteOn(another, STRANGER, "2026-11-17");

        // when
        var actual = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(MENTEE)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-11-17T23:30:00Z")).build());

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(2, 4, 0, 0));
        assertThat(jdbc.query("SELECT id, planned_by FROM mentoring_meeting WHERE organization_id = :org ORDER BY id",
                Map.of("org", ORG), (rs, i) -> Arrays.asList(rs.getLong(1), rs.getString(2)))).containsExactly(
                        Arrays.asList(passed, null), Arrays.asList(theMentors, MENTOR), Arrays.asList(heldToday, null));
        assertThat(jdbc.queryForList("SELECT id FROM mentoring_meeting", Map.of(), Long.class)).doesNotContain(upcoming,
                today);
    }

    @Test
    void erasureRemovesAMeetingHeldOnlyByTheirNotes() {

        // given
        var pairing = rows.pairing(guild, MENTOR, MENTEE);
        var heldByThemAlone = rows.meeting(pairing, MENTOR, "2026-11-10");
        rows.noteOn(pairing, MENTEE, "2026-11-10");
        var heldByBoth = rows.meeting(pairing, MENTOR, "2026-11-12");
        rows.noteOn(pairing, MENTEE, "2026-11-12");
        rows.noteOn(pairing, MENTOR, "2026-11-12");

        // when
        var actual = eraser.erase(erasureOf(MENTEE));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(3, 1, 0, 0));
        assertThat(jdbc.queryForList("SELECT id FROM mentoring_meeting WHERE organization_id = :org",
                Map.of("org", ORG), Long.class)).containsExactly(heldByBoth).doesNotContain(heldByThemAlone);
    }

    @Test
    void erasingBothSidesDeletesThePairing() {

        // given
        var pairing = rows.pairing(guild, MENTOR, MENTEE);
        rows.topic(pairing, "Kafka");
        eraser.erase(erasureOf(MENTEE));

        // when
        var actual = eraser.erase(erasureOf(MENTOR));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(1, 0, 0, 0));
        assertThat(rows.count("mentoring_pairing")).isZero();
        assertThat(rows.count("mentoring_topic")).isZero();
    }

    @Test
    void erasureDeletesOpenProposalsAvailabilityAndPointersNamingThePerson() {

        // given
        rows.availability(guild, MENTEE);
        rows.availability(guild, STRANGER);
        rows.proposal(guild, MENTOR, MENTEE, "Sent by the mentee");
        rows.proposal(guild, MENTEE, STRANGER, "Sent to the mentee");
        rows.proposal(guild, MENTOR, STRANGER, "Between two others");
        var others = rows.pairing(guild, MENTOR, STRANGER);
        var topic = rows.topic(others, "People to meet");
        rows.memberOnTopic(others, topic, MENTEE);
        rows.linkOnTopic(others, topic, "https://kept.example");

        // when
        var actual = eraser.erase(erasureOf(MENTEE));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(4, 0, 0, 0));
        assertThat(strings("SELECT user_id FROM mentoring_availability WHERE organization_id = :org"))
                .containsExactly(STRANGER);
        assertThat(strings("SELECT message FROM mentoring_proposal WHERE organization_id = :org"))
                .containsExactly("Between two others");
        assertThat(strings("SELECT url FROM mentoring_pointer WHERE organization_id = :org"))
                .containsExactly("https://kept.example");
        assertThat(sides(others)).containsExactly(MENTOR, STRANGER);
    }

    @Test
    void erasureForgetsMentoringNoticesAboutThePerson() {

        // given
        var notices = List.of("mentoring_proposal", "mentoring_declined", "mentoring_ended", "mentoring_words_asked",
                "mentoring_words_received");
        notices.forEach(type -> rows.expiredNotice(type, "pairing:7:" + MENTEE));
        rows.expiredNotice("mentoring_meeting_set", "pairing:7@1794844318000:" + MENTEE);
        rows.expiredNotice("mentoring_meeting_reminder", "pairing:7@2026-11-19:" + MENTEE);
        rows.expiredNotice("mentoring_proposal_reopened", "proposal:7@1794844318000:" + MENTEE);
        rows.expiredNotice("mentoring_ended", "pairing:7:" + MENTOR);
        rows.expiredNotice("mentoring_ended", "pairing:7:x" + MENTEE);
        rows.expiredNotice("open_to_questions", "7:kafka:" + MENTEE);

        // when
        var actual = eraser.erase(erasureOf(MENTEE));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(8, 0, 0, 0));
        assertThat(strings("SELECT message_type || ' ' || subject_ref FROM communication_expired_message "
                + "WHERE organization_id = :org")).containsExactlyInAnyOrder("mentoring_ended pairing:7:" + MENTOR,
                        "mentoring_ended pairing:7:x" + MENTEE, "open_to_questions 7:kafka:" + MENTEE);
    }

    @Test
    void leavesTheSamePersonInAnotherOrganizationUntouched() {

        // given
        var elsewhere = new MentoringRows(jdbc, OTHER_ORG);
        var pairing = elsewhere.pairing(elsewhere.community("Other Guild"), MENTOR, MENTEE);
        elsewhere.availability(1L, MENTEE);
        elsewhere.note(pairing, MENTEE, "Kept elsewhere");
        elsewhere.words(pairing, "Kept elsewhere");

        // when
        var actual = eraser.erase(erasureOf(MENTEE));

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(0, 0, 0, 0));
        assertThat(elsewhere.count("mentoring_pairing")).isEqualTo(1);
        assertThat(elsewhere.count("mentoring_availability")).isEqualTo(1);
        assertThat(elsewhere.count("mentoring_note")).isEqualTo(1);
        assertThat(elsewhere.count("mentoring_words")).isEqualTo(1);
    }

    private List<String> sides(long pairingId) {

        return jdbc.query("SELECT mentor_user_id, mentee_user_id FROM mentoring_pairing WHERE id = :id",
                Map.of("id", pairingId), (rs, i) -> Arrays.asList(rs.getString(1), rs.getString(2))).getFirst();
    }

    private List<Boolean> answers(long pairingId) {

        return jdbc.query("SELECT mentor_said_yes, mentee_said_yes FROM mentoring_pairing WHERE id = :id",
                Map.of("id", pairingId), (rs, i) -> List.of(rs.getBoolean(1), rs.getBoolean(2))).getFirst();
    }

    private List<String> strings(String sql) {

        return jdbc.queryForList(sql, Map.of("org", ORG), String.class);
    }

    private static MemberErasure erasureOf(String userId) {

        return MemberErasure.builder().organizationId(ORG).userId(userId).surrogateId("erased:test-surrogate")
                .erasedAt(Instant.parse("2026-11-16T10:00:00Z")).build();
    }
}
