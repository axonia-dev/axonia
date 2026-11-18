package dev.axonia.mentoring.adapter.out.privacy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.ExpiredSubjects;
import dev.axonia.communication.api.MessageType;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * What an erased member leaves of their mentoring. Their availability, every proposal they sent or received, the notes
 * they wrote and every pointer naming them go. The words of each of their pairings go too, whichever side they were on:
 * written by them as a mentee, or written to them as a mentor. A pairing keeps the other person's side, its topics and
 * the other person's notes, with the erased side cleared. Before their notes go, the upcoming meetings they planned go,
 * today read in UTC as of the erasure, and so does every meeting only their notes held, which would otherwise come back
 * as owed or upcoming; every other meeting they planned stays with who planned it cleared. A pairing left with nobody
 * on either side goes whole.
 *
 * <p>
 * Leaving each community on the way to erasure ends their ongoing pairings first, and the notice that ending would send
 * names them, so it goes with communication's own erasure of every message about them; the partner reads the ending on
 * My mentoring, as a pairing with a deleted member. The record of every expired mentoring notice about them goes here.
 */
@Component
@RequiredArgsConstructor
public class MentoringMemberDataEraser implements MemberDataEraser {

    private static final List<MessageType> NOTICES = List.of(MessageType.MENTORING_PROPOSAL,
            MessageType.MENTORING_DECLINED, MessageType.MENTORING_ENDED, MessageType.MENTORING_WORDS_ASKED,
            MessageType.MENTORING_WORDS_RECEIVED, MessageType.MENTORING_MEETING_SET,
            MessageType.MENTORING_MEETING_REMINDER, MessageType.MENTORING_PROPOSAL_REOPENED);

    private final NamedParameterJdbcTemplate jdbc;
    private final ExpiredSubjects expiredSubjects;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.<String, Object>of("org", erasure.organizationId(), "user", erasure.userId(), "today",
                LocalDate.ofInstant(erasure.erasedAt(), ZoneOffset.UTC));
        long deleted = jdbc.update("""
                DELETE FROM mentoring_meeting m
                 WHERE m.organization_id = :org AND m.planned_by = :user AND m.meeting_on >= :today
                   AND NOT EXISTS (SELECT 1 FROM mentoring_note n
                                    WHERE n.organization_id = m.organization_id AND n.pairing_id = m.pairing_id
                                      AND n.meeting_on = m.meeting_on)
                """, params);
        deleted += jdbc.update("""
                DELETE FROM mentoring_meeting m
                 WHERE m.organization_id = :org
                   AND EXISTS (SELECT 1 FROM mentoring_note n
                                WHERE n.organization_id = m.organization_id AND n.pairing_id = m.pairing_id
                                  AND n.meeting_on = m.meeting_on AND n.author_user_id = :user)
                   AND NOT EXISTS (SELECT 1 FROM mentoring_note n
                                    WHERE n.organization_id = m.organization_id AND n.pairing_id = m.pairing_id
                                      AND n.meeting_on = m.meeting_on AND n.author_user_id <> :user)
                """, params);
        deleted += jdbc.update("DELETE FROM mentoring_availability WHERE organization_id = :org AND user_id = :user",
                params);
        deleted += jdbc.update("DELETE FROM mentoring_proposal WHERE organization_id = :org"
                + " AND (mentor_user_id = :user OR mentee_user_id = :user)", params);
        deleted += jdbc.update("DELETE FROM mentoring_pointer WHERE organization_id = :org AND target_user_id = :user",
                params);
        deleted += jdbc.update("DELETE FROM mentoring_note WHERE organization_id = :org AND author_user_id = :user",
                params);
        deleted += jdbc.update("""
                DELETE FROM mentoring_words w
                 USING mentoring_pairing p
                 WHERE p.id = w.pairing_id
                   AND p.organization_id = :org
                   AND w.organization_id = :org
                   AND (p.mentor_user_id = :user OR p.mentee_user_id = :user)
                """, params);
        deleted += jdbc.update("""
                DELETE FROM mentoring_pairing
                 WHERE organization_id = :org
                   AND ((mentor_user_id = :user AND mentee_user_id IS NULL)
                        OR (mentee_user_id = :user AND mentor_user_id IS NULL))
                """, params);
        long cleared = jdbc.update(
                "UPDATE mentoring_meeting SET planned_by = NULL WHERE organization_id = :org AND planned_by = :user",
                params);
        cleared += jdbc.update("UPDATE mentoring_pairing SET mentor_user_id = NULL, mentor_said_yes = false"
                + " WHERE organization_id = :org AND mentor_user_id = :user", params);
        cleared += jdbc.update("UPDATE mentoring_pairing SET mentee_user_id = NULL, mentee_said_yes = false"
                + " WHERE organization_id = :org AND mentee_user_id = :user", params);
        var suffix = MentoringSubject.aboutSuffix(erasure.userId());
        for (var type : NOTICES) {
            deleted += expiredSubjects.forgetEndingWith(erasure.organizationId(), type, suffix);
        }
        return new ErasureCounts(deleted, cleared, 0, 0);
    }
}
