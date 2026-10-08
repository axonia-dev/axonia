package dev.axonia.events.internal;

import java.sql.Timestamp;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ErasureCounts;
import dev.axonia.MemberDataEraser;
import dev.axonia.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * The one module where two columns each carry two dispositions, decided by a different axis apiece. A record of what
 * someone did is community history and is anonymised; a commitment nobody will now honour is not, and is cleared or
 * deleted. {@code community_session.facilitator_user_id} and {@code event_slot_presenter} split on time: a session that
 * has not happened yet is not history. {@code action_item.assignee_user_id} splits on outcome: a closed item has
 * nothing left to honour, so its assignment becomes history like everything else, but an open one is still a live
 * dispatch. Without either split a community sees "Deleted member" listed as facilitating next Tuesday's session, and a
 * finished action item's record of who actually did the work is destroyed along with the assignment.
 *
 * <p>
 * The instant is a plain named parameter supplied by the caller's Clock, never SQL {@code now()}: the application
 * drives time, and a database-driven comparison would make the test that proves the time-based split a tautology. It is
 * also what every UPDATE here stamps {@code updated_at} with, alongside a {@code version} bump: these rows map to
 * {@code @Version} entities, and an UPDATE that left the version alone would leave a concurrently-loaded entity's
 * optimistic-lock predicate still matching, so the next save writes the whole row back with the pre-erasure subject on
 * it. An identifier resurrected after a legal erasure, with nothing anywhere to notice.
 *
 * <p>
 * {@code event_slot_presenter} carries no {@code organization_id} - it is reached through its parents, exactly as
 * {@link EventsDataPurger} reaches it.
 *
 * <p>
 * {@code calendar_subscription} is a live credential, not history, so its row is deleted outright rather than
 * anonymised - the same DELETE_ROW disposition {@code event_slot_presenter} takes for a future slot, for the same
 * reason: nothing is left to keep unnamed once the credential it backs is gone.
 */
@Component
@RequiredArgsConstructor
public class EventsMemberDataEraser implements MemberDataEraser {

    private static final String FUTURE_SLOTS = """
            SELECT sl.id FROM event_slot sl
              JOIN event_section sec ON sl.section_id = sec.id
              JOIN community_session cs ON sec.session_id = cs.id
             WHERE cs.organization_id = :org AND cs.scheduled_at > :erasedAt
            """;

    private static final String PAST_SLOTS = """
            SELECT sl.id FROM event_slot sl
              JOIN event_section sec ON sl.section_id = sec.id
              JOIN community_session cs ON sec.session_id = cs.id
             WHERE cs.organization_id = :org AND cs.scheduled_at <= :erasedAt
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final CalendarSubscriptionRepository calendarSubscriptionRepository;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId(), "surrogate",
                erasure.surrogateId(), "erasedAt", Timestamp.from(erasure.erasedAt()));

        var deleted = jdbc.update(
                "DELETE FROM event_slot_presenter WHERE user_id = :user AND slot_id IN (" + FUTURE_SLOTS + ")", params)
                + calendarSubscriptionRepository.deleteByOrganizationIdAndUserId(erasure.organizationId(),
                        erasure.userId());

        var cleared = jdbc
                .update("UPDATE community_session SET facilitator_user_id = NULL, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND facilitator_user_id = :user "
                        + "AND scheduled_at > :erasedAt", params)
                + jdbc.update("UPDATE action_item SET assignee_user_id = NULL, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND assignee_user_id = :user "
                        + "AND outcome = 'OPEN'", params);

        var anonymised = jdbc
                .update("UPDATE community_session SET facilitator_user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND facilitator_user_id = :user "
                        + "AND scheduled_at <= :erasedAt", params)
                + jdbc.update(
                        "UPDATE topic SET proposer_user_id = :surrogate, version = version + 1, "
                                + "updated_at = :erasedAt WHERE organization_id = :org AND proposer_user_id = :user",
                        params)
                + jdbc.update("UPDATE topic_vote SET user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND user_id = :user", params)
                + jdbc.update("UPDATE session_attendance SET user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND user_id = :user", params)
                // event_slot_presenter is the one table here with neither column: it is a plain link row carrying
                // only slot_id and user_id, with no @Version to invalidate and no updated_at to stamp.
                + jdbc.update("UPDATE event_slot_presenter SET user_id = :surrogate "
                        + "WHERE user_id = :user AND slot_id IN (" + PAST_SLOTS + ")", params)
                + jdbc.update("UPDATE action_item SET assignee_user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND assignee_user_id = :user "
                        + "AND outcome <> 'OPEN'", params)
                + jdbc.update(
                        "UPDATE action_item SET raised_by_user_id = :surrogate, version = version + 1, "
                                + "updated_at = :erasedAt WHERE organization_id = :org AND raised_by_user_id = :user",
                        params)
                // action_item_checkpoint has neither column, like event_slot_presenter.
                + jdbc.update("UPDATE action_item_checkpoint SET decided_by_user_id = :surrogate "
                        + "WHERE organization_id = :org AND decided_by_user_id = :user", params);

        return new ErasureCounts(deleted, cleared, anonymised, 0);
    }
}
