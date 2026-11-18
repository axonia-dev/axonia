package dev.axonia.mentoring.adapter.out.privacy;

import java.sql.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.ExportScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MentoringPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "mentoring-export-org";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String THIRD = "third-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private MentoringPersonalDataExporter exporter;

    private MentoringRows rows;

    private long mentoring;

    @BeforeEach
    void seed() {

        rows = new MentoringRows(jdbc, ORG);
        var guild = rows.community("Platform Guild");
        rows.availability(guild, TARGET);
        rows.availability(guild, NEIGHBOUR);
        rows.proposal(guild, NEIGHBOUR, TARGET, "Sent by the target");
        rows.proposal(guild, TARGET, THIRD, "Sent to the target");
        rows.proposal(guild, NEIGHBOUR, THIRD, "Between two others");

        var mentored = rows.pairing(guild, NEIGHBOUR, TARGET);
        var topic = rows.topic(mentored, "Kafka");
        rows.linkOnTopic(mentored, topic, "https://topic.example");
        var targetsNote = rows.note(mentored, TARGET, "Target note");
        rows.noteTopic(targetsNote, topic);
        rows.meeting(mentored, TARGET, topic);
        rows.meeting(mentored, NEIGHBOUR, "2026-12-03");
        rows.linkOnNote(mentored, targetsNote, "https://target.example");
        var neighboursNote = rows.note(mentored, NEIGHBOUR, "Neighbour note");
        rows.linkOnNote(mentored, neighboursNote, "https://neighbour.example");
        rows.words(mentored, "Words by the target");
        rows.openWords(mentored);

        mentoring = rows.pairing(guild, TARGET, THIRD);
        rows.words(mentoring, "Words to the target");

        var others = rows.pairing(guild, NEIGHBOUR, THIRD);
        rows.memberOnTopic(others, rows.topic(others, "Unrelated"), TARGET);
        rows.note(others, THIRD, "Third note");
        rows.words(others, "Words between others");
    }

    @Test
    void aMembersExportCarriesWhatTheyTookPartInAndNeverThePartner() {

        // when
        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        // then
        assertThat(fragment.fileName()).isEqualTo("mentoring.json");
        assertThat(rowsOf(fragment, "mentoring_availability")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("user_id", TARGET)
                        .containsEntry("community", "Platform Guild").containsEntry("mentee_limit", 3));
        assertThat(rowsOf(fragment, "mentoring_proposal"))
                .extracting(row -> row.get("message"), row -> row.get("role"), row -> row.get("direction"))
                .containsExactlyInAnyOrder(tuple("Sent by the target", "MENTEE", "SENT"),
                        tuple("Sent to the target", "MENTOR", "RECEIVED"));
        assertThat(rowsOf(fragment, "mentoring_proposal")).first()
                .satisfies(row -> assertThat(row.get("topics")).isEqualTo(List.of("Kafka", "Feedback")));
        assertThat(rowsOf(fragment, "mentoring_pairing")).extracting(row -> row.get("role"))
                .containsExactlyInAnyOrder("MENTEE", "MENTOR");
        assertThat(rowsOf(fragment, "mentoring_topic")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("label", "Kafka")
                        .containsEntry("community", "Platform Guild").containsKey("pairing_started_at"));
        assertThat(rowsOf(fragment, "mentoring_note")).extracting(row -> row.get("body"))
                .containsExactly("Target note");
        assertThat(rowsOf(fragment, "mentoring_pointer")).extracting(row -> row.get("url"))
                .containsExactlyInAnyOrder("https://topic.example", "https://target.example");
        assertThat(rowsOf(fragment, "mentoring_words")).extracting(row -> row.get("body"), row -> row.get("role"))
                .containsExactlyInAnyOrder(tuple("Words by the target", "MENTEE"),
                        tuple("Words to the target", "MENTOR"));
        assertThat(json(fragment)).doesNotContain(NEIGHBOUR, THIRD, "Neighbour note", "neighbour.example",
                "Between two others", "Unrelated", "Third note", "Words between others", "mentor_user_id",
                "mentee_user_id", "target_user_id");
    }

    @Test
    void theOrganizationsExportCarriesEveryRowWithThePairingsPeople() {

        // when
        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        // then
        assertThat(rowsOf(fragment, "mentoring_availability")).hasSize(2);
        assertThat(rowsOf(fragment, "mentoring_proposal")).hasSize(3);
        assertThat(rowsOf(fragment, "mentoring_pairing"))
                .extracting(row -> row.get("mentor_user_id"), row -> row.get("mentee_user_id"))
                .containsExactlyInAnyOrder(tuple(NEIGHBOUR, TARGET), tuple(TARGET, THIRD), tuple(NEIGHBOUR, THIRD));
        assertThat(rowsOf(fragment, "mentoring_topic")).hasSize(2);
        assertThat(rowsOf(fragment, "mentoring_note")).extracting(row -> row.get("author_user_id"))
                .containsExactlyInAnyOrder(TARGET, NEIGHBOUR, THIRD);
        assertThat(rowsOf(fragment, "mentoring_pointer")).hasSize(4)
                .anySatisfy(row -> assertThat(row).containsEntry("target_user_id", TARGET)
                        .containsEntry("mentor_user_id", NEIGHBOUR).containsEntry("mentee_user_id", THIRD));
        assertThat(rowsOf(fragment, "mentoring_words")).hasSize(3);
        assertThat(rowsOf(fragment, "mentoring_pairing")).allSatisfy(row -> assertThat(row).doesNotContainKey("role"));
    }

    @Test
    void aNoteIsExportedWithItsMeetingDayAndTopicLabels() {

        // when
        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        // then
        assertThat(rowsOf(fragment, "mentoring_note")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("topics", List.of("Kafka"))
                        .containsEntry("meeting_on", Date.valueOf("2026-11-12")).doesNotContainKey("topic_id"));
    }

    @Test
    void theExportListsTheMeetingsTheyPlanned() {

        // when
        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        // then
        assertThat(rowsOf(fragment, "mentoring_meeting")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("meeting_on", Date.valueOf("2026-11-26"))
                        .containsEntry("topics", List.of("Kafka")).containsKey("planned_at")
                        .containsEntry("community", "Platform Guild").doesNotContainKey("pairing_id"));
        assertThat(rowsOf(fragment, "mentoring_pairing"))
                .allSatisfy(row -> assertThat(row).doesNotContainKeys("next_meeting_on", "next_meeting_set_at"));
    }

    @Test
    void theOrganizationsExportNamesWhoPlannedEachMeeting() {

        // when
        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        // then
        assertThat(rowsOf(fragment, "mentoring_meeting")).extracting(row -> row.get("planned_by"))
                .containsExactlyInAnyOrder(TARGET, NEIGHBOUR);
        assertThat(rowsOf(fragment, "mentoring_note")).extracting(row -> row.get("topics"))
                .containsExactlyInAnyOrder(List.of("Kafka"), List.of(), List.of());
    }

    @Test
    void whenTheMentorOpenedTheWordsGoesToTheMentorAndNeverToTheMentee() {

        // given
        rows.openWords(mentoring);

        // when
        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));
        var whole = exporter.export(new ExportScope.WholeOrganization(ORG));

        // then
        assertThat(rowsOf(fragment, "mentoring_words"))
                .extracting(row -> row.get("role"), row -> row.get("mentor_opened_at") != null)
                .containsExactlyInAnyOrder(tuple("MENTEE", false), tuple("MENTOR", true));
        assertThat(rowsOf(fragment, "mentoring_words"))
                .allSatisfy(row -> assertThat(row).doesNotContainKey("opened_at"));
        assertThat(rowsOf(whole, "mentoring_words")).filteredOn(row -> row.get("mentor_opened_at") != null).hasSize(2);
        assertThat(rowsOf(fragment, "mentoring_pairing"))
                .allSatisfy(row -> assertThat(row).doesNotContainKey("words_asked_at"));
    }

    @Test
    void aPersonScopeExportsNothing() {

        // when
        var fragment = exporter.export(new ExportScope.Person(TARGET));

        // then
        assertThat(fragment.fileName()).isEqualTo("mentoring.json");
        assertThat(fragment.holdsNothing()).isTrue();
    }
}
