package dev.axonia.agent.adapter.in.mcp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import dev.axonia.agent.adapter.in.mcp.TagReports.TagReportView;
import dev.axonia.agent.domain.AgentScope;
import dev.axonia.community.api.MemberDirectory;
import dev.axonia.community.api.Memberships;
import dev.axonia.events.api.ActionItems;
import dev.axonia.events.api.Attendances;
import dev.axonia.events.api.CreateOneOffRequest;
import dev.axonia.events.api.ProposeTopicRequest;
import dev.axonia.events.api.RaiseActionItemRequest;
import dev.axonia.events.api.RecordSlotRequest;
import dev.axonia.events.api.RecordedSlot;
import dev.axonia.events.api.ScheduledAt;
import dev.axonia.events.api.SectionTemplate;
import dev.axonia.events.api.Sections;
import dev.axonia.events.api.SessionClosure;
import dev.axonia.events.api.SessionClosureRequest;
import dev.axonia.events.api.SessionDeletion;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.Slots;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.Topics;
import dev.axonia.knowledge.api.Artifacts;
import dev.axonia.knowledge.api.AttachResourceRequest;
import dev.axonia.knowledge.api.CreateArtifactRequest;
import dev.axonia.knowledge.api.Resources;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * The write agent tools: contributing to a community's library, proposing a topic, raising an action item, signing up
 * to present in a session's slot, signalling attendance, joining a community, attaching or detaching a session's
 * artifacts, backfilling a community's history by creating a past session, adding a section to it, recording who
 * presented a slot, correcting a slot's presenter afterwards, and closing it as held, and deleting a session that never
 * took place.
 *
 * <p>
 * Most calls are additive and not idempotent: calling one twice with the same arguments either creates a second record
 * or hits a domain conflict (e.g. "already going"). Two exceptions: {@code session.attachArtifact} is idempotent (it
 * returns any existing matching attachment), and the two destructive tools, {@code session.detachArtifact} and
 * {@code session.delete}, are each gated behind a scope of their own.
 *
 * <p>
 * The four tools that tag what they create also answer with a {@link TagReports tag report}, so an agent sees which of
 * its tags were new to the community before the vocabulary sprawls.
 */
@Component
@RequiredArgsConstructor
class WriteTools {

    private final AgentToolSupport support;
    private final Artifacts artifacts;
    private final Topics topics;
    private final Slots slots;
    private final Attendances attendance;
    private final Memberships memberships;
    private final Resources resources;
    private final Sessions sessions;
    private final Sections sections;
    private final ActionItems actionItems;
    private final SessionDeletion sessionDeletion;
    private final SessionClosure sessionClosure;
    private final TagReports tagReports;

    /**
     * The tail shared by both write tools' actor-reference rule, spelled the same way so the two descriptions cannot
     * drift on it: an agent that learns this part from one tool must find it true of the other.
     */
    private static final String REFERENCE_RULE = "A reference resolves against everyone in the organization, members "
            + "of the community first. One that matches nobody, or more than one person, REFUSES the write rather "
            + "than crediting you by default, so retrying it unchanged will fail again: call community.people to find "
            + "the exact user id and pass that.";

    private static final String OMIT_TO_CREDIT_YOURSELF = "Omit it and the work is credited to you, which is the "
            + "live path. Supply it ONLY to credit somebody else: ";

    private static final String PROPOSER_RULE = OMIT_TO_CREDIT_YOURSELF + "the check fires whenever it is present, "
            + "even when it names you, and only an organizer may use it. " + REFERENCE_RULE;

    private static final String CONTRIBUTOR_RULE = OMIT_TO_CREDIT_YOURSELF + "the check fires only when it names "
            + "somebody other than you, and only an organizer, or a member the community lets credit contributions, "
            + "may use it. " + REFERENCE_RULE;

    /** Grouped into one record (rather than seven method params) to stay under the parameter-count limit. */
    record ContributeParams(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Title", required = true) String title,
            @McpToolParam(description = "Artifact type id", required = true) Long typeId,
            @McpToolParam(description = "http(s) URL", required = true) String url,
            @McpToolParam(description = "Optional description", required = false) String description,
            @McpToolParam(description = "Optional tags. Reuse names from tag.list.", required = false) Set<String> tags,
            @McpToolParam(description = "Optional display name, email or user id of the person whose work this is. "
                    + CONTRIBUTOR_RULE, required = false) String contributor) {
    }

    /** Grouped into one record (rather than six method params) to stay under the parameter-count limit. */
    record ProposeTopicParams(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "REQUEST or OFFER", required = true) String kind,
            @McpToolParam(description = "Title", required = true) String title,
            @McpToolParam(description = "Optional description", required = false) String description,
            @McpToolParam(description = "Optional tags. Reuse names from tag.list.", required = false) List<String> tags,
            @McpToolParam(description = "Optional display name, email or user id of the person whose proposal this is. "
                    + PROPOSER_RULE, required = false) String proposer) {
    }

    /** Grouped into one record (rather than seven method params) to stay under the parameter-count limit. */
    record RaiseActionItemParams(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Title", required = true) String title,
            @McpToolParam(description = "Optional description", required = false) String description,
            @McpToolParam(description = "Optional tags. Reuse names from tag.list.", required = false) List<String> tags,
            @McpToolParam(description = "Optional display name, email or user id of the person the work is dispatched "
                    + "to. Omit it to leave the item unassigned. " + REFERENCE_RULE, required = false) String assignee,
            @McpToolParam(description = "Optional due date, ISO yyyy-MM-dd", required = false) String dueDate,
            @McpToolParam(description = "Optional id of the held session this was raised at", required = false) Long originSessionId) {
    }

    record ContributeResult(Long id, String title, String url, TagReportView tagReport) {
    }

    record RaiseActionItemResult(Long id, String title, String assigneeUserId, LocalDate dueDate,
            TagReportView tagReport) {
    }

    record ProposeResult(Long id, String title, String kind, TagReportView tagReport) {
    }

    record SignupResult(Long sessionId, Long sectionId, Long slotId) {
    }

    record AttendanceResult(Long id, Long sessionId, Instant createdAt) {
    }

    record JoinResult(Long id, Long communityId, String role, Instant joinedAt) {
    }

    record AttachResult(Long attachmentId, Long sessionId, Long artifactId, Long segmentId) {
    }

    record DetachResult(Long sessionId, Long artifactId, Long segmentId) {
    }

    /** Grouped into one record (rather than four method params) to stay under the parameter-count limit. */
    record CreateSessionParams(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Session title", required = true) String title,
            @McpToolParam(description = "ISO-8601 instant. May be in the past: this is how history is backfilled "
                    + "from another tool.", required = true) String scheduledAt,
            @McpToolParam(description = "Length in minutes", required = false) Integer durationMinutes) {
    }

    /** Grouped into one record (rather than seven method params) to stay under the parameter-count limit. */
    record AddSlotParams(@McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "Section id", required = true) Long sectionId,
            @McpToolParam(description = "Slot title", required = true) String title,
            @McpToolParam(description = "Length in minutes", required = true) Integer durationMinutes,
            @McpToolParam(description = "Optional abstract or notes", required = false) String notes,
            @McpToolParam(description = "Who presented, as display names, emails or user ids. Each is resolved against "
                    + "everyone in the organization, members of the community first, and crediting makes nobody a "
                    + "member; names that cannot be placed are reported back and the slot is left open for them. Use "
                    + "community.people to place one.", required = false) List<String> presenters,
            @McpToolParam(description = "Tags saying what the talk was about, up to 10. They are what makes the talk "
                    + "count toward its presenters' expertise, and a slot recorded here has no other source of "
                    + "subject, so supply them, reusing names from tag.list.", required = false) List<String> tags) {
    }

    /** Grouped into one record (rather than four method params) to stay under the parameter-count limit. */
    record CloseSessionParams(@McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "true to email every member a wrap-up of the session once the community's "
                    + "grace period has passed. Omit it, or pass false, and no wrap-up is ever sent for this session, "
                    + "which is what a backfilled session wants. Set it only for a session that has just "
                    + "happened.", required = false) Boolean wrapUp,
            @McpToolParam(description = "Optional http(s) link to the recording of the whole session, shown on the "
                    + "session as its Recording. A recording of one talk belongs in the library instead: "
                    + "library.contribute, then session.attachArtifact with that slot's id.", required = false) String recordingUrl,
            @McpToolParam(description = "Optional follow-up notes, shown on the held session", required = false) String notes) {
    }

    /**
     * {@code wrapUp} is the wrap-up's release state as the session page shows it: {@code OFF} means no wrap-up will be
     * sent, {@code SCHEDULED} that it goes out at {@code wrapUpReleaseAt}.
     */
    record ClosedSessionView(Long sessionId, String status, String recordingUrl, String wrapUp,
            Instant wrapUpReleaseAt) {
    }

    record DeletedSessionView(Long sessionId) {
    }

    record CreatedSessionView(Long sessionId, String title, Instant scheduledAt, String status) {
    }

    record CreatedSectionView(Long sectionId, Long sectionTypeId, String title) {
    }

    record RecordedSlotView(Long slotId, String title, List<String> assigned, List<String> unmatched,
            List<String> ambiguous, TagReportView tagReport) {
    }

    /**
     * {@code candidates} is how many people an AMBIGUOUS reference matched, and 0 for every other outcome. Told only
     * "ambiguous", an agent cannot tell two people who genuinely share a name from a reference that was too vague, and
     * so cannot tell whether to ask a human or simply try a fuller name.
     *
     * <p>
     * It is a lower bound, not a fact: the person search stops at {@link MemberDirectory#CANDIDATE_LIMIT}, so a count
     * equal to that limit means "at least that many". Nothing on this surface may claim otherwise, and nothing on it
     * may name the limit as a literal either: the tool description below concatenates the constant itself, so a limit
     * that moves cannot leave the surface describing the old one.
     */
    record PresenterAssignmentView(boolean assigned, String reason, int candidates) {
    }

    @McpTool(name = "library.contribute", description = "Add a link to a community's library. Get a valid typeId from "
            + "library.artifactTypes first. Leave contributor out to contribute as yourself; set it only when the "
            + "work is somebody else's, which only an organizer or a member the community lets credit contributions "
            + "may do, and refuses the write if the name cannot be " + "placed on exactly one person. "
            + TagReports.REUSE_TAGS, annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public ContributeResult contribute(
            @McpToolParam(description = "Artifact contribution details", required = true) ContributeParams params) {
        return support.run("library.contribute", AgentScope.LIBRARY_CONTRIBUTE, () -> {
            var pending = tagReports.inCommunity(params.slug(), params.tags());
            var artifact = artifacts.capture(params.slug(), new CreateArtifactRequest(params.title(), params.typeId(),
                    params.url(), params.description(), params.tags(), null, params.contributor()));
            return new ContributeResult(artifact.id(), artifact.title(), artifact.url(),
                    pending.reportOn(artifact.tags()));
        });
    }

    @McpTool(name = "topic.propose", description = "Propose a topic to a community. Kind is REQUEST (I want this) or "
            + "OFFER (I can present this). Leave proposer out to propose as yourself; set it only when the proposal is "
            + "somebody else's, which is an organizer-only act and refuses the write if the name cannot be placed on "
            + "exactly one person. "
            + TagReports.REUSE_TAGS, annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public ProposeResult proposeTopic(
            @McpToolParam(description = "Topic proposal details", required = true) ProposeTopicParams params) {
        return support.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> {
            var pending = tagReports.inCommunity(params.slug(), params.tags());
            var topic = topics.propose(params.slug(), new ProposeTopicRequest(proposableKind(params.kind()),
                    params.title(), params.description(), params.tags(), params.proposer()));
            return new ProposeResult(topic.id(), topic.title(), String.valueOf(topic.kind()),
                    pending.reportOn(topic.tags()));
        });
    }

    /**
     * Action items are their own aggregate, so this is not a kind of topic: no votes, no slot, no talk-shaped
     * lifecycle. The checkpoint is deliberately not on this surface - an item raised here late-binds to whichever
     * session comes next, which is the answer an agent wants by default and the only one it can give without knowing
     * the community's schedule.
     */
    @McpTool(name = "actionItem.raise", description = "Raise an action item in a community: a piece of operational "
            + "work tracked to done, not a talk to give. It is answered at whichever session comes next. Leave "
            + "assignee out to leave the work unassigned; set it to dispatch the work to "
            + "anybody in the organization. "
            + TagReports.REUSE_TAGS, annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public RaiseActionItemResult raiseActionItem(
            @McpToolParam(description = "Action item details", required = true) RaiseActionItemParams params) {
        return support.run("actionItem.raise", AgentScope.ACTION_ITEM_RAISE, () -> {
            var pending = tagReports.inCommunity(params.slug(), params.tags());
            var item = actionItems.raise(params.slug(),
                    RaiseActionItemRequest.builder().title(params.title()).description(params.description())
                            .tags(params.tags()).assignee(params.assignee()).dueDate(dueDate(params.dueDate()))
                            .originSessionId(params.originSessionId()).build());
            return new RaiseActionItemResult(item.id(), item.title(), item.assigneeUserId(), item.dueDate(),
                    pending.reportOn(item.tags()));
        });
    }

    // The date arrives as text so a malformed one is an agent-readable refusal rather than a deserialization failure.
    private static LocalDate dueDate(String dueDate) {

        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dueDate);
        } catch (DateTimeParseException e) {
            throw new ValidationException("INVALID_DUE_DATE", "dueDate must be an ISO date, yyyy-MM-dd.");
        }
    }

    /**
     * Action items are their own aggregate, raised through {@code actionItem.raise} rather than proposed as a topic.
     * {@link TopicKind} no longer carries {@code ACTION} at all, so a caller sending it would otherwise fall straight
     * through to {@link TopicKind#valueOf} and fail with a generic {@code IllegalArgumentException}; this guard turns
     * that into a clean validation error for a kind agents may still send.
     */
    private static TopicKind proposableKind(String kind) {

        if ("ACTION".equals(kind)) {
            throw new ValidationException("UNSUPPORTED_TOPIC_KIND",
                    "kind must be REQUEST or OFFER. Use actionItem.raise for operational work.");
        }
        return TopicKind.valueOf(kind);
    }

    @McpTool(name = "session.signup", description = "Sign yourself up to present in a session's slot. Use session.get to find the sectionId "
            + "and slotId.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public SignupResult signUp(@McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "Section id", required = true) Long sectionId,
            @McpToolParam(description = "Slot id", required = true) Long slotId) {
        return support.run("session.signup", AgentScope.SESSION_SIGNUP, () -> {
            slots.signUp(sessionId, sectionId, slotId);
            return new SignupResult(sessionId, sectionId, slotId);
        });
    }

    @McpTool(name = "attendance.signal", description = "Signal that you are going to a session.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public AttendanceResult signalAttendance(
            @McpToolParam(description = "Session id", required = true) Long sessionId) {
        return support.run("attendance.signal", AgentScope.ATTENDANCE_SIGNAL, () -> {
            var signaled = attendance.signal(sessionId);
            return new AttendanceResult(signaled.id(), signaled.sessionId(), signaled.createdAt());
        });
    }

    @McpTool(name = "community.join", description = "Join a community by slug. Use communities.list to discover communities you are not yet a "
            + "member of.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public JoinResult joinCommunity(@McpToolParam(description = "Community slug", required = true) String slug) {
        return support.run("community.join", AgentScope.COMMUNITY_JOIN, () -> {
            var membership = memberships.join(slug);
            return new JoinResult(membership.id(), membership.communityId(), String.valueOf(membership.role()),
                    membership.joinedAt());
        });
    }

    @McpTool(name = "session.attachArtifact", description = "Attach an existing library artifact to a session, for "
            + "example a recording. Get the artifactId from library.search or library.contribute, and the sessionId "
            + "from schedule.read. Attaching the same artifact twice returns the existing attachment.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public AttachResult attachArtifact(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "Optional slot id, as returned by session.get or session.addSlot. Omit it "
                    + "to attach the artifact to the whole session rather than to one slot.", required = false) Long segmentId,
            @McpToolParam(description = "Artifact id", required = true) Long artifactId) {
        return support.run("session.attachArtifact", AgentScope.SESSION_ATTACH, () -> {
            var attachment = resources.attach(slug, sessionId, new AttachResourceRequest(segmentId, artifactId));
            return new AttachResult(attachment.id(), attachment.sessionId(), attachment.artifactId(),
                    attachment.segmentId());
        });
    }

    @McpTool(name = "session.detachArtifact", description = "Remove an artifact from a session, or from one of its "
            + "slots. Pass the sessionId, and the segmentId and artifact id of the row as session.artifacts returned "
            + "it; omit segmentId for a row that has none. The attacher or an organizer removes an attachment; its "
            + "origin naming this session is then cleared only for the contributor or an organizer. A row shown only "
            + "through its origin is removable by its contributor or an organizer.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false))
    public DetachResult detachArtifact(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "The row's segmentId from session.artifacts. Omit it for a row without one.", required = false) Long segmentId,
            @McpToolParam(description = "Artifact id, from session.artifacts", required = true) Long artifactId) {
        return support.run("session.detachArtifact", AgentScope.SESSION_DETACH, () -> {
            resources.detach(slug, sessionId, artifactId, segmentId);
            return new DetachResult(sessionId, artifactId, segmentId);
        });
    }

    @McpTool(name = "session.create", description = "Create a one-off session in a community. scheduledAt may be in "
            + "the past, which is how a community's history is backfilled from another tool; a past session comes "
            + "back with status SCHEDULED, and stays so until session.close records that it actually took "
            + "place.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public CreatedSessionView createSession(
            @McpToolParam(description = "Session details", required = true) CreateSessionParams params) {
        return support.run("session.create", AgentScope.SCHEDULE_MANAGE, () -> {
            var session = sessions.createOneOff(params.slug(), new CreateOneOffRequest(params.title(),
                    scheduledAt(params.scheduledAt()), null, params.durationMinutes(), List.of()));
            return new CreatedSessionView(session.id(), session.title(), session.scheduledAt(),
                    String.valueOf(session.status()));
        });
    }

    /**
     * The tool's parameter is an absolute instant rather than a civil date and a zone, so it carries no ambiguity for
     * the server to resolve: read back in UTC it composes to exactly the moment the caller named. Stating {@code UTC}
     * here is therefore not a default standing in for an unknown zone, it is the zone the value was already in.
     */
    private static ScheduledAt scheduledAt(String instant) {

        var utc = Instant.parse(instant).atZone(ZoneOffset.UTC);
        return new ScheduledAt(utc.toLocalDate(), utc.toLocalTime(), "UTC");
    }

    @McpTool(name = "session.addSection", description = "Add a section to a session. Get a valid sectionTypeId from "
            + "events.sectionTypes first. Works on a past session too.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public CreatedSectionView addSection(@McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "Section type id, from events.sectionTypes", required = true) Long sectionTypeId,
            @McpToolParam(description = "Optional section title", required = false) String title,
            @McpToolParam(description = "Optional time budget in minutes", required = false) Integer durationMinutes) {
        return support.run("session.addSection", AgentScope.SCHEDULE_MANAGE, () -> {
            var session = sections.addSection(sessionId, new SectionTemplate(sectionTypeId, title, durationMinutes));
            var added = session.sections().get(session.sections().size() - 1);
            return new CreatedSectionView(added.id(), added.sectionTypeId(), added.title());
        });
    }

    @McpTool(name = "session.addSlot", description = "Add a slot to a session's section, recording who presented it "
            + "and what it was about. Unlike session.signup, this never signs you up: name the presenters explicitly, "
            + "or leave the list empty for an open slot. Always tag it: this tool never fills a slot from a backlog "
            + "topic, so the slot has no topic to take its subject from, and an untagged talk counts toward nobody's "
            + "expertise. "
            + TagReports.REUSE_TAGS, annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public RecordedSlotView addSlot(@McpToolParam(description = "Slot details", required = true) AddSlotParams params) {
        return support.run("session.addSlot", AgentScope.SCHEDULE_MANAGE, () -> {
            var pending = tagReports.inSession(params.sessionId(), params.tags());
            var recorded = slots.recordSlot(params.sessionId(), params.sectionId(), new RecordSlotRequest(
                    params.title(), params.durationMinutes(), params.notes(), params.presenters(), params.tags()));
            var report = recorded.presenters();
            return new RecordedSlotView(recorded.slotId(), params.title(), report.assigned(), report.unmatched(),
                    report.ambiguous(), pending.reportOn(storedTags(recorded)));
        });
    }

    private static List<String> storedTags(RecordedSlot recorded) {

        return recorded.session().sections().stream().flatMap(section -> section.slots().stream())
                .filter(slot -> slot.id().equals(recorded.slotId())).flatMap(slot -> slot.tags().stream()).toList();
    }

    @McpTool(name = "slot.addPresenter", description = "Record that somebody presented an existing slot, naming them "
            + "by display name, email or user id, from anybody in the organization, members of the community first. A "
            + "name that matches nobody, or more than one person, assigns nobody and comes back with a reason: "
            + "NOT_FOUND, or AMBIGUOUS with how many people it matched (a lower bound: the search stops counting at "
            + MemberDirectory.CANDIDATE_LIMIT
            + "). Use community.people to place the name exactly. Assigning the same person twice changes nothing.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public PresenterAssignmentView addPresenter(
            @McpToolParam(description = "Session id", required = true) Long sessionId,
            @McpToolParam(description = "Section id", required = true) Long sectionId,
            @McpToolParam(description = "Slot id", required = true) Long slotId,
            @McpToolParam(description = "Display name, email or user id", required = true) String presenter) {
        return support.run("slot.addPresenter", AgentScope.SCHEDULE_MANAGE, () -> {
            var assignment = slots.addPresenter(sessionId, sectionId, slotId, presenter);
            return new PresenterAssignmentView(assignment.assigned(), assignment.reason(), assignment.candidates());
        });
    }

    @McpTool(name = "session.close", description = "Record that a past session took place: its status becomes "
            + "HELD, optionally with the link to its recording and follow-up notes, and its talks count as given. "
            + "The last step of a backfill, after session.create, session.addSection and session.addSlot. No "
            + "wrap-up email is sent unless wrapUp is true, so closing a backfilled session mails nobody. "
            + "Organizers only, once per session, and not before the session has started. A session that never "
            + "took place is not closed: remove it with session.delete.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public ClosedSessionView closeSession(
            @McpToolParam(description = "How the session went", required = true) CloseSessionParams params) {
        return support.run("session.close", AgentScope.SCHEDULE_MANAGE, () -> {
            var closed = sessionClosure.closeAsHeld(params.sessionId(),
                    SessionClosureRequest.builder().wrapUp(Boolean.TRUE.equals(params.wrapUp()))
                            .recordingUrl(params.recordingUrl()).notes(params.notes()).build());
            return new ClosedSessionView(closed.id(), String.valueOf(closed.status()),
                    AgentToolSupport.blankAsAbsent(closed.recordingUrl()),
                    String.valueOf(closed.wrapUpStatus().state()), closed.wrapUpReleaseAt());
        });
    }

    @McpTool(name = "session.delete", description = "Delete a session that never took place, for example one "
            + "backfilled by mistake, with its sections, slots and attendance. Its topics go back to the backlog, its "
            + "talks stop counting toward anyone's expertise, action items waiting on it move on to the next session, "
            + "members who saved it no longer see it, and artifacts attached to it stay in the library. A held "
            + "session cannot be deleted, nor can a session of a series: skip or cancel that one instead. Organizers "
            + "only, and it cannot be undone.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false))
    public DeletedSessionView deleteSession(@McpToolParam(description = "Session id", required = true) Long sessionId) {
        return support.run("session.delete", AgentScope.SESSION_DELETE, () -> {
            sessionDeletion.delete(sessionId);
            return new DeletedSessionView(sessionId);
        });
    }
}
