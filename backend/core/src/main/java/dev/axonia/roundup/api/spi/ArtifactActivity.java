package dev.axonia.roundup.api.spi;

import java.util.List;
import java.util.Map;

/**
 * Read-only artifact activity for the sessions one quarter held. Implemented by
 * dev.axonia.knowledge.adapter.out.roundup.ArtifactActivityAdapter, so knowledge depends on the roundup and never the
 * other way round.
 *
 * <p>
 * The organization arrives as a parameter because a roundup is assembled with no JWT in scope, and that is not the
 * whole contract. Callers MUST run inside {@code OrganizationScope.runAs(organizationId, ...)}, and that {@code runAs}
 * MUST wrap the {@code @Transactional} entry point rather than sit inside it: {@code artifact} and
 * {@code artifact_attachment} are both row-level-secured on {@code app.current_org}, which Postgres chooses when the
 * transaction acquires its connection. A scheduler thread with no scope leaves that setting unset, and under the
 * {@code axonia_app} role this method then answers an empty list instead of failing. No adapter test can catch it
 * either: Testcontainers connects as the table owner, and ownership bypasses the policy.
 *
 * <p>
 * <b>Capture is measured by attachment, never by creation date.</b> An earlier version counted rows whose
 * {@code created_at} fell inside the quarter, organization-wide and unrelated to any session. Knowledge is routinely
 * written down weeks after the session it documents, so that reading made a May session's recording invisible to Q2 and
 * counted it as Q3's output, while an upload attached to nothing at all counted as a quarter's work. The attachment is
 * the only fact that says a piece of knowledge belongs to a quarter, and it is what keeps the letter and the session
 * page describing the same set of resources.
 *
 * <p>
 * <b>An ARCHIVED artifact is no longer capture the letter may print, reversing an earlier rule.</b> Capture is still
 * measured by attachment - archiving does not delete the attachment row, only flips {@code artifact.status} - but this
 * port used to answer regardless of that status, on the argument that a capture act happened whether or not somebody
 * later took the artifact down. That argument holds for a COUNT OF ACTIVITY, which is what
 * {@code ArtifactSignalsAdapter} still measures community health with, and it does not hold here: this port answers a
 * TITLE and a LIVE LINK that go out to every present employee, unretractable, pointing at a resource an organizer
 * deliberately withdrew. So the query filters on {@code status = 'ACTIVE'}, exactly as
 * {@code SessionResourceRepositoryImpl} does for the session page.
 *
 * <p>
 * That does not make the two reads identical, and the claim they are would be too strong. The session page is this
 * port's query UNION artifacts carrying a {@code provenance_session_id} with no attachment row at all - a state
 * {@code ArtifactService#autoAttach} leaves behind when provenance names a session but no segment and the follow-up
 * attach does not land. Those resources show on the session page and are invisible to the letter. The narrow claim that
 * holds is the one that matters for an inbox: <b>everything this port answers is on the session page, and none of it is
 * archived</b>. The reverse containment is a known gap, deliberately not closed here, because widening the letter to
 * provenance-only artifacts would print resources that no organizer ever attached to the session being reported.
 *
 * <p>
 * Titles DO cross this boundary, reversing this port's original refusal. That refusal reasoned that an artifact title
 * can carry anything its contributor typed and so should not be broadcast; but the title is already on a page every
 * member of the organization can open, and a section that can only ever print a number is a section that says nothing.
 * The archival filter above is what keeps that justification true rather than merely plausible. The URL crosses for the
 * same reason and is rendered as a link only when it is http(s), by the shell's existing rule.
 */
public interface ArtifactActivity {

    /**
     * Every live artifact attached to any of the given sessions, ordered by session and then by title. An artifact
     * attached to two of them is answered once per session: the letter shows it under both, and deduplicating for the
     * headline count is the caller's job, not this port's. An artifact attached TWICE to one session - the shape a
     * shared deck takes when it is attached to two of the session's segments, or once at session level and once at talk
     * level - is answered once for that session, because the row carries no segment and two identical rows under one
     * session row would render the same link twice.
     *
     * <p>
     * {@code sessionCommunityIds} maps each session id to the community it was actually held by - the caller already
     * knows this, it is exactly {@code SessionActivity.HeldSession#communityId} for each session it is asking about -
     * and an artifact attached to a session under a DIFFERENT community than the one the artifact itself belongs to is
     * excluded. Attachment alone is not enough: an id from a request body is org-visible and sequential by design, so a
     * resource could be attached to a session naming a community other than the artifact's own before the write side
     * refused that; this is what keeps such a row, if one exists from before that fix shipped, out of the quarterly
     * letter.
     *
     * <p>
     * An empty {@code sessionCommunityIds} answers an empty list without querying, because {@code IN ()} is not valid
     * SQL.
     */
    List<CapturedArtifact> capturedForSessions(String organizationId, Map<Long, Long> sessionCommunityIds);

    /**
     * One captured artifact, as much of it as a mail may print. {@code typeLabel} is the library type's own label
     * ("Recording", "Slides", "Notes") and is null for an artifact whose type row has gone.
     *
     * <p>
     * <b>{@code typeLabel} has no reader today and is held deliberately.</b> {@code RoundupFacts#resource} drops it,
     * because "Recording" printed beside a link titled "Recording of the May session" is the same word twice. It stays
     * on the record for an open question - whether a resource is shown against the talk it belongs to rather than
     * against the session - where a row that has lost the session's own heading needs a noun to say what it is. Nobody
     * should read its presence as evidence that a template renders it.
     */
    record CapturedArtifact(long sessionId, String title, String url, String typeLabel) {
    }
}
