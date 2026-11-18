package dev.axonia.mentoring.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * What mentoring holds about a member. Availability and proposals exist only to describe the people on them, so they go
 * whole. A pairing belongs to both of its people, so erasing one clears their side and the other keeps the pairing, its
 * topics and their own notes; the notes the erased person wrote and every pointer naming them go, and a next meeting
 * they set is cleared. Topics carry no person of their own and are exported through their pairing.
 *
 * <p>
 * A member pointer naming the member is the one subject row a member's export leaves out on purpose: it sits in two
 * other people's private journal, and handing it over would reveal that their pairing exists. Erasure still deletes it.
 *
 * <p>
 * {@code mentoring_words} names nobody on its own row: its author is the pairing's mentee and its reader the pairing's
 * mentor, so the exporter and the eraser reach it through the pairing, and the words go when either side is erased.
 * When the mentor opened them is the mentor's own: it is exported to the mentor and the Organization, never to the
 * mentee, as {@code mentor_opened_at}. Every child row is exported with its community's name and its pairing's start in
 * place of the pairing's id.
 */
@Component
public class MentoringPersonalDataFootprint implements PersonalDataFootprint {

    static final String AVAILABILITY = "mentoring_availability";
    static final String PROPOSAL = "mentoring_proposal";
    static final String PAIRING = "mentoring_pairing";
    static final String TOPIC = "mentoring_topic";
    static final String NOTE = "mentoring_note";
    static final String NOTE_TOPIC = "mentoring_note_topic";
    static final String MEETING = "mentoring_meeting";
    static final String MEETING_TOPIC = "mentoring_meeting_topic";
    static final String POINTER = "mentoring_pointer";
    static final String WORDS = "mentoring_words";

    static final String COMMUNITY_LABEL = "community";
    static final String ROLE_LABEL = "role";
    static final String DIRECTION_LABEL = "direction";
    static final String PAIRING_STARTED_LABEL = "pairing_started_at";
    static final String TOPICS_LABEL = "topics";
    static final String MENTOR_OPENED_LABEL = "mentor_opened_at";

    private static final Set<String> CHILD_LABELS = Set.of(COMMUNITY_LABEL, PAIRING_STARTED_LABEL);

    @Override
    public List<TableFootprint> tables() {

        return List.of(availability(), proposal(), pairing(), topic(), note(), noteTopic(), meeting(), meetingTopic(),
                pointer(), words());
    }

    private static TableFootprint availability() {

        return TableFootprint.builder().table(AVAILABILITY).category(DataCategory.MENTORING_AVAILABILITY)
                .personal(List.of(subject("user_id", Disposition.DELETE_ROW)))
                .notPersonal(Set.of("organization_id", "community_id", "taking_mentees", "mentee_limit",
                        "looking_for_mentor", "updated_at"))
                .exportedContext(Set.of("taking_mentees", "mentee_limit", "looking_for_mentor", "updated_at"))
                .exportedLabels(Set.of(COMMUNITY_LABEL)).build();
    }

    private static TableFootprint proposal() {

        return TableFootprint.builder().table(PROPOSAL).category(DataCategory.MENTORING)
                .personal(List.of(subject("mentor_user_id", Disposition.DELETE_ROW),
                        subject("mentee_user_id", Disposition.DELETE_ROW), content("message")))
                .notPersonal(Set.of("id", "organization_id", "community_id", "proposed_by", "topics", "status",
                        "created_at", "answered_at", "reopened_at"))
                .exportedContext(Set.of("proposed_by", "topics", "status", "created_at", "answered_at", "reopened_at"))
                .exportedLabels(Set.of(COMMUNITY_LABEL, ROLE_LABEL, DIRECTION_LABEL)).build();
    }

    private static TableFootprint pairing() {

        return TableFootprint.builder().table(PAIRING).category(DataCategory.MENTORING)
                .personal(List.of(subject("mentor_user_id", Disposition.CLEAR),
                        subject("mentee_user_id", Disposition.CLEAR),
                        subject("next_meeting_set_by", Disposition.CLEAR)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "started_at", "ended_at", "end_reason",
                        "next_meeting_on", "next_meeting_set_at"))
                .exportedContext(
                        Set.of("started_at", "ended_at", "end_reason", "next_meeting_on", "next_meeting_set_at"))
                .exportedLabels(Set.of(COMMUNITY_LABEL, ROLE_LABEL)).build();
    }

    private static TableFootprint topic() {

        return TableFootprint.builder().table(TOPIC).category(DataCategory.MENTORING).personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "pairing_id", "label", "area", "state", "position",
                        "created_at"))
                .exportedContext(Set.of("label", "area", "state", "position", "created_at"))
                .exportedLabels(CHILD_LABELS).build();
    }

    private static TableFootprint note() {

        return TableFootprint.builder().table(NOTE).category(DataCategory.MENTORING)
                .personal(List.of(subject("author_user_id", Disposition.DELETE_ROW), content("body")))
                .notPersonal(Set.of("id", "organization_id", "pairing_id", "meeting_on", "written_at", "edited_at"))
                .exportedContext(Set.of("meeting_on", "written_at", "edited_at"))
                .exportedLabels(Set.of(COMMUNITY_LABEL, PAIRING_STARTED_LABEL, TOPICS_LABEL)).build();
    }

    /** Which topics a note names: exported as the labels of its note's topics, never on its own. */
    private static TableFootprint noteTopic() {

        return TableFootprint.builder().table(NOTE_TOPIC).category(DataCategory.MENTORING).personal(List.of())
                .notPersonal(Set.of("organization_id", "note_id", "topic_id")).build();
    }

    private static TableFootprint meeting() {

        return TableFootprint.builder().table(MEETING).category(DataCategory.MENTORING)
                .personal(List.of(subject("planned_by", Disposition.CLEAR)))
                .notPersonal(Set.of("id", "organization_id", "pairing_id", "meeting_on", "planned_at"))
                .exportedContext(Set.of("meeting_on", "planned_at"))
                .exportedLabels(Set.of(COMMUNITY_LABEL, PAIRING_STARTED_LABEL, TOPICS_LABEL)).build();
    }

    /** Which topics a meeting names: exported as the labels of its meeting's topics, never on its own. */
    private static TableFootprint meetingTopic() {

        return TableFootprint.builder().table(MEETING_TOPIC).category(DataCategory.MENTORING).personal(List.of())
                .notPersonal(Set.of("organization_id", "meeting_id", "topic_id")).build();
    }

    private static TableFootprint pointer() {

        return TableFootprint.builder().table(POINTER).category(DataCategory.MENTORING)
                .personal(List.of(subject("target_user_id", Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "pairing_id", "topic_id", "note_id", "kind", "target_id",
                        "url", "created_at"))
                .exportedContext(Set.of("kind", "url", "created_at")).exportedLabels(CHILD_LABELS).build();
    }

    private static TableFootprint words() {

        return TableFootprint.builder().table(WORDS).category(DataCategory.WORDS_FROM_MENTEES)
                .personal(List.of(content("body")))
                .notPersonal(Set.of("pairing_id", "organization_id", "written_at", "updated_at", "opened_at"))
                .exportedContext(Set.of("written_at", "updated_at"))
                .exportedLabels(Set.of(COMMUNITY_LABEL, PAIRING_STARTED_LABEL, ROLE_LABEL, MENTOR_OPENED_LABEL))
                .build();
    }

    private static ColumnFootprint subject(String column, Disposition disposition) {

        return new ColumnFootprint(column, Classification.SUBJECT, disposition);
    }

    private static ColumnFootprint content(String column) {

        return new ColumnFootprint(column, Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW);
    }
}
