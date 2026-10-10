package dev.axonia.events.adapter.out.privacy;

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
 * A session's schedule is community history and outlives whoever ran it, so every person named on it -
 * {@code topic.proposer_user_id}, {@code community_session.facilitator_user_id}, {@code topic_vote.user_id},
 * {@code session_attendance.user_id}, {@code event_slot_presenter.user_id}, {@code action_item.raised_by_user_id} and
 * {@code action_item_checkpoint.decided_by_user_id} - is anonymised in place rather than taking the row with them.
 * {@code event_slot_presenter} is the one exception to "in place": a presenter row for a slot whose session has not
 * happened yet is not history at all, so {@link EventsMemberDataEraser} deletes that row outright rather than
 * anonymising it, and only a past presenter row is anonymised as declared here. {@code action_item.assignee_user_id} is
 * different in kind: it is a live pointer to whoever the item is currently dispatched to, not a historical fact, so it
 * is cleared rather than anonymised - unless the item is no longer {@code OPEN}, at which point there is nothing left
 * to honour and the assignment is community history like everything else, so it is anonymised instead of cleared. The
 * structural tables - sections, series, slots and their tag children - describe the shape of a session or series, not a
 * person, and carry nothing personal. {@code location}, {@code join_url} and {@code joining_details}, on the series and
 * on the session alike, are where a community meets rather than a fact about anybody who attends, so they are declared
 * not personal on both, exactly as {@code recording_url} already is.
 *
 * <p>
 * Each of the three split columns is declared under every disposition it actually carries, not just the common one.
 * {@code action_item.assignee_user_id} always was; {@code community_session.facilitator_user_id} and
 * {@code event_slot_presenter.user_id} were not, and declaring only {@code ANONYMISE} told a reader that a future
 * session keeps a surrogate facilitator and a future presenter row survives - the opposite of what the split exists to
 * do, and a manifest that disagrees with its eraser is worth less than no manifest.
 *
 * <p>
 * Every surrogate key is out of the export and a name is in its place. A row reading {@code community_id: 41,
 * topic_id: 902} tells the person who asked for their data nothing they did not already not know, and a key is only
 * ever useful to somebody holding the rest of the database. So {@code id}, {@code organization_id}, {@code version} and
 * every foreign key leave {@code exportedContext} and each one that carried meaning comes back as a label: the
 * community's name, the topic's title, the session it happened at and that session's date, the slot presented, the
 * action item a checkpoint decided. {@code community_session}'s {@code wrap_up_release_at} and
 * {@code upcoming_release_at} leave too, for a different reason - they are scheduler plumbing saying when a digest may
 * go out, not a fact about anybody on the session.
 */
@Component
public class EventsPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(topic(), topicVote(), sessionAttendance(), communitySession(), eventSlotPresenter(),
                eventSection(), eventSectionType(), eventSeries(), eventSeriesSectionType(), eventSlot(), topicTag(),
                eventSlotTag(), calendarSubscription(), actionItem(), actionItemTag(), actionItemCheckpoint());
    }

    private TableFootprint topic() {

        return TableFootprint.builder().table("topic").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(
                        List.of(new ColumnFootprint("proposer_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "kind", "title", "description", "status",
                        "created_at", "updated_at", "version"))
                .exportedContext(Set.of("kind", "title", "description", "status", "created_at", "updated_at"))
                .exportedLabels(Set.of("community")).build();
    }

    private TableFootprint topicVote() {

        return TableFootprint.builder().table("topic_vote").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "topic_id", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("created_at", "updated_at")).exportedLabels(Set.of("topic")).build();
    }

    private TableFootprint sessionAttendance() {

        return TableFootprint.builder().table("session_attendance").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "session_id", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("created_at", "updated_at")).exportedLabels(Set.of("session", "session_date"))
                .build();
    }

    /**
     * {@code facilitator_user_id} carries three dispositions rather than one: anonymised for a session that has already
     * happened, cleared for one still scheduled, and deleted with the row when the whole tenant goes. Only the first
     * was declared, which told a reader that a future session keeps a surrogate facilitator - the opposite of what
     * {@link EventsMemberDataEraser} does, and the reason this split exists at all.
     */
    private TableFootprint communitySession() {

        return TableFootprint.builder().table("community_session").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of(
                        new ColumnFootprint("facilitator_user_id", Classification.SUBJECT, Disposition.ANONYMISE),
                        new ColumnFootprint("facilitator_user_id", Classification.SUBJECT, Disposition.CLEAR)))
                .notPersonal(Set.of("id", "organization_id", "series_id", "community_id", "status", "scheduled_at",
                        "has_time", "time_zone", "duration_minutes", "notes", "recording_url", "location", "join_url",
                        "joining_details", "created_at", "updated_at", "version", "occurrence_date", "title",
                        "closed_at", "wrap_up_release_at", "upcoming_release_at"))
                .exportedContext(Set.of("status", "scheduled_at", "has_time", "time_zone", "duration_minutes", "notes",
                        "recording_url", "location", "join_url", "joining_details", "occurrence_date", "title",
                        "closed_at", "created_at", "updated_at"))
                .exportedLabels(Set.of("community", "series")).build();
    }

    /**
     * The presenter half of the same time-based split: a past presenter row is anonymised, a future one is deleted
     * outright, and both dispositions are declared for the one column that carries them.
     */
    private TableFootprint eventSlotPresenter() {

        return TableFootprint.builder().table("event_slot_presenter").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.ANONYMISE),
                        new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("slot_id")).exportedContext(Set.of())
                .exportedLabels(Set.of("slot", "session", "session_date")).build();
    }

    private TableFootprint eventSection() {

        return TableFootprint.builder().table("event_section").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of())
                .notPersonal(
                        Set.of("id", "session_id", "position", "section_type_id", "title", "duration_minutes", "notes"))
                .build();
    }

    private TableFootprint eventSectionType() {

        return TableFootprint.builder().table("event_section_type").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "label", "expects_presenter",
                        "created_at", "updated_at", "version", "icon", "accent_color"))
                .build();
    }

    private TableFootprint eventSeries() {

        return TableFootprint.builder().table("event_series").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "community_id", "title", "cadence_count", "cadence_unit",
                        "preferred_time", "time_zone", "location", "join_url", "joining_details", "status",
                        "created_at", "updated_at", "version", "starts_on", "duration_minutes"))
                .build();
    }

    private TableFootprint eventSeriesSectionType() {

        return TableFootprint.builder().table("event_series_section_type").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of())
                .notPersonal(Set.of("series_id", "position", "section_type_id", "title", "duration_minutes")).build();
    }

    private TableFootprint eventSlot() {

        return TableFootprint.builder().table("event_slot").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of())
                .notPersonal(Set.of("id", "section_id", "position", "topic_id", "title", "notes", "duration_minutes"))
                .build();
    }

    private TableFootprint topicTag() {

        return TableFootprint.builder().table("topic_tag").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of()).notPersonal(Set.of("topic_id", "tag")).build();
    }

    private TableFootprint eventSlotTag() {

        return TableFootprint.builder().table("event_slot_tag").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of()).notPersonal(Set.of("slot_id", "tag")).build();
    }

    /**
     * A calendar subscription is a live credential belonging to one person, not community history: the row exists only
     * to resolve the feed URL it backs, so {@code user_id} is {@code DELETE_ROW} rather than anonymised, and erasing
     * the member takes the row - and the URL - with it, exactly as {@link EventsMemberDataEraser} does.
     *
     * <p>
     * {@code token_hash} is a credential verifier, not something an access request asks for, and is withheld from the
     * export the same way {@code personal_access_token.token_hash} is. {@code last_fetched_agent} is likewise withheld.
     * {@code last_fetched_at} is opted into {@code exportedContext} and the community is named rather than numbered:
     * without it a member subscribed to more than one community would export indistinguishable rows, and existence,
     * community and last use together answer the request without helping anybody resolve or confirm the URL.
     */
    private TableFootprint calendarSubscription() {

        return TableFootprint.builder().table("calendar_subscription").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "token_hash", "created_at",
                        "last_fetched_at", "last_fetched_agent", "version"))
                .exportedContext(Set.of("last_fetched_at")).exportedLabels(Set.of("community")).build();
    }

    /**
     * {@code assignee_user_id} carries both dispositions, split on {@code outcome}: an {@code OPEN} item is a live
     * dispatch nobody will now honour, a closed one is community history and keeps a surrogate.
     * {@code raised_by_user_id} is history unconditionally.
     */
    private TableFootprint actionItem() {

        return TableFootprint.builder().table("action_item").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(
                        List.of(new ColumnFootprint("raised_by_user_id", Classification.SUBJECT, Disposition.ANONYMISE),
                                new ColumnFootprint("assignee_user_id", Classification.SUBJECT, Disposition.CLEAR),
                                new ColumnFootprint("assignee_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "title", "description", "due_date",
                        "origin_session_id", "checkpoint_session_id", "outcome", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("title", "description", "due_date", "outcome", "created_at", "updated_at"))
                .exportedLabels(Set.of("community", "origin_session", "checkpoint_session")).build();
    }

    private TableFootprint actionItemTag() {

        return TableFootprint.builder().table("action_item_tag").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of()).notPersonal(Set.of("action_item_id", "tag")).build();
    }

    private TableFootprint actionItemCheckpoint() {

        return TableFootprint.builder().table("action_item_checkpoint").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List
                        .of(new ColumnFootprint("decided_by_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "action_item_id", "session_id", "outcome", "decided_at"))
                .exportedContext(Set.of("outcome", "decided_at")).exportedLabels(Set.of("action_item", "session"))
                .build();
    }
}
