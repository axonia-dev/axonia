package dev.axonia.events.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.events.api.PresenterAssignment;
import dev.axonia.events.api.RecordSlotRequest;
import dev.axonia.events.api.RecordedSlot;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SlotCleared;
import dev.axonia.events.api.Slots;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SlotEventCoords;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.SlotSubject;
import dev.axonia.events.domain.Tags;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlotService implements Slots {

    private final SessionStore sessionStore;
    private final TopicLifecycle topicLifecycle;
    private final SessionMapper sessionMapper;
    private final SessionAuthorizer sessionAuthorizer;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final PresenterResolver presenterResolver;
    private final SlotEventPublisher slotEventPublisher;

    @Transactional
    public Session addSlot(Long sessionId, Long sectionId, AddSlotRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireMember(session.getCommunityId());
        requireContributable(session);
        var tags = SlotSubject.own(request.topicId(), request.tags());
        var userId = currentUser.userId();
        // A new slot never has a prior topic, so the guard always applies here: attaching one by id is always a
        // genuine queue-topics act, never the pass-through an unchanged re-attachment on an edit gets.
        var topicId = attachTopic(
                new TopicAttachment(organizationId, session.getCommunityId(), request.topicId(), null, userId));
        var section = SessionSections.section(session, sectionId);

        var presenters = new HashSet<SlotPresenter>();
        presenters.add(SlotPresenter.builder().userId(userId).build());
        var slots = new ArrayList<>(SessionSections.nonNullSlots(section.getSlots()));
        slots.add(
                SlotRecord.builder().topicId(topicId).title(request.title()).durationMinutes(request.durationMinutes())
                        .notes(request.notes()).presenters(presenters).tags(SlotSubject.refs(tags)).build());

        var saved = sessionStore
                .save(SessionSections.replaceSection(session, sectionId, section.toBuilder().slots(slots).build()));
        var savedSection = SessionSections.section(saved, sectionId);
        var savedSlots = SessionSections.nonNullSlots(savedSection.getSlots());
        var savedSlot = savedSlots.get(savedSlots.size() - 1);
        slotEventPublisher.publish(new SlotEventCoords(organizationId, session.getCommunityId(), savedSlot.getId()),
                List.of(userId), topicId, List.copyOf(tags));
        return sessionMapper.toSession(saved);
    }

    /**
     * Records a slot presented by named people, e.g. when backfilling history from another tool, or an organizer
     * editing a past session. Unlike {@link #addSlot}, the caller is never assigned as a presenter: each free-text
     * reference in {@code request.presenters()} is resolved against the community's members, and only the ones that
     * resolve to exactly one member are assigned. The rest are reported, not guessed.
     */
    @Override
    @Transactional
    public RecordedSlot recordSlot(Long sessionId, Long sectionId, RecordSlotRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        var communityId = session.getCommunityId();
        sessionAuthorizer.requireScheduleManager(communityId, currentUser.userId());

        var resolution = presenterResolver.resolve(communityId, request.presenters());
        // Two different free-text references (e.g. a name and an email for the same person, exactly the dirty data a
        // migration carries) can resolve to the same user id. Dedup by user id, not by reference: SlotPresenter has
        // no equals/hashCode, so a HashSet of freshly-built refs would not catch the duplicate and would later violate
        // the event_slot_presenter unique key.
        var presenters = resolution.userIds().stream().distinct().map(id -> SlotPresenter.builder().userId(id).build())
                .collect(Collectors.toCollection(HashSet::new));

        var tags = Tags.clean(request.tags());
        var section = SessionSections.section(session, sectionId);
        var slots = new ArrayList<>(SessionSections.nonNullSlots(section.getSlots()));
        slots.add(SlotRecord.builder().topicId(null).title(request.title()).durationMinutes(request.durationMinutes())
                .notes(request.notes()).presenters(presenters).tags(SlotSubject.refs(tags)).build());

        var saved = sessionStore
                .save(SessionSections.replaceSection(session, sectionId, section.toBuilder().slots(slots).build()));
        var savedSlots = SessionSections.nonNullSlots(SessionSections.section(saved, sectionId).getSlots());
        var savedSlot = savedSlots.get(savedSlots.size() - 1);
        slotEventPublisher.publish(new SlotEventCoords(organizationId, communityId, savedSlot.getId()),
                resolution.userIds(), null, List.copyOf(tags));
        return new RecordedSlot(sessionMapper.toSession(saved), savedSlot.getId(), resolution.report());
    }

    @Transactional
    public Session queueTopic(Long sessionId, Long sectionId, QueueTopicRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        var userId = currentUser.userId();
        var communityId = session.getCommunityId();
        requireMayQueueTopics(communityId, userId);

        var topic = topicLifecycle.schedule(organizationId, session.getCommunityId(), request.topicId());

        var section = SessionSections.section(session, sectionId);
        var title = request.title() == null ? topic.getTitle() : request.title();
        var notes = request.notes() == null ? topic.getDescription() : request.notes();
        var slots = new ArrayList<>(SessionSections.nonNullSlots(section.getSlots()));
        // No tags of its own: the topic owns the subject, so this slot never holds a copy that can go stale against it.
        slots.add(SlotRecord.builder().topicId(request.topicId()).title(title).notes(notes)
                .durationMinutes(request.durationMinutes()).presenters(new HashSet<>()).tags(new HashSet<>()).build());

        return sessionMapper.toSession(sessionStore
                .save(SessionSections.replaceSection(session, sectionId, section.toBuilder().slots(slots).build())));
    }

    @Transactional
    public Session reorderSlots(Long sessionId, Long sectionId, ReorderSlotsRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        sessionAuthorizer.requireOrganizer(session, currentUser.userId());
        var section = SessionSections.section(session, sectionId);
        var slots = SessionSections.nonNullSlots(section.getSlots());
        requireCompleteSlotOrder(slots, request.slotIds());
        Map<Long, SlotRecord> byId = slots.stream().collect(Collectors.toMap(SlotRecord::getId, s -> s));
        var ordered = request.slotIds().stream().map(byId::get).toList();
        return sessionMapper.toSession(sessionStore
                .save(SessionSections.replaceSection(session, sectionId, section.toBuilder().slots(ordered).build())));
    }

    private void requireCompleteSlotOrder(List<SlotRecord> slots, List<Long> slotIds) {

        var expected = slots.stream().map(SlotRecord::getId).sorted().toList();
        if (!slotIds.stream().sorted().toList().equals(expected)) {
            throw new ConflictException("SLOT_ORDER_MISMATCH", "Order must list every slot exactly once");
        }
    }

    @Override
    @Transactional
    public Session signUp(Long sessionId, Long sectionId, Long slotId) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireMember(session.getCommunityId());
        requireContributable(session);
        var section = SessionSections.section(session, sectionId);
        var slot = SessionSections.slot(section, slotId);
        var userId = currentUser.userId();

        var presenters = new HashSet<>(SessionSections.presenters(slot));
        if (presenters.stream().anyMatch(p -> p.getUserId().equals(userId))) {
            throw new ConflictException("ALREADY_PRESENTING", "You are already presenting this slot");
        }
        presenters.add(SlotPresenter.builder().userId(userId).build());
        var updated = SessionSections.replaceSlot(section, slotId, slot.toBuilder().presenters(presenters).build());
        var saved = sessionStore.save(SessionSections.replaceSection(session, sectionId, updated));
        var presenterIds = presenters.stream().map(SlotPresenter::getUserId).toList();
        slotEventPublisher.publish(new SlotEventCoords(organizationId, session.getCommunityId(), slotId), presenterIds,
                slot.getTopicId(), SessionSections.tags(slot));
        return sessionMapper.toSession(saved);
    }

    @Transactional
    public Session editSlot(Long sessionId, Long sectionId, Long slotId, EditSlotRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        var section = SessionSections.section(session, sectionId);
        var slot = SessionSections.slot(section, slotId);
        sessionAuthorizer.requireSlotOwnerOrManager(organizationId, session, slot, currentUser.userId());
        // Omitted tags keep the subject the slot already has, the same "null means keep current" convention
        // durationMinutes uses below. The invariant is checked against these effective tags, not the request's.
        var tags = SlotSubject.edited(request.topicId(), request.tags(), SessionSections.tags(slot));
        var previousTopicId = slot.getTopicId();
        var topicId = attachTopic(new TopicAttachment(organizationId, session.getCommunityId(), request.topicId(),
                previousTopicId, currentUser.userId()));
        topicLifecycle.releaseReplaced(organizationId, previousTopicId, topicId);
        var updated = slot.toBuilder().title(request.title()).topicId(topicId).notes(request.notes())
                .durationMinutes(
                        request.durationMinutes() != null ? request.durationMinutes() : slot.getDurationMinutes())
                .tags(SlotSubject.refs(tags)).build();
        var saved = sessionStore.save(SessionSections.replaceSection(session, sectionId,
                SessionSections.replaceSlot(section, slotId, updated)));
        var presenterIds = SessionSections.presenters(slot).stream().map(SlotPresenter::getUserId).toList();
        slotEventPublisher.publish(new SlotEventCoords(organizationId, session.getCommunityId(), slotId), presenterIds,
                topicId, List.copyOf(tags));
        return sessionMapper.toSession(saved);
    }

    @Transactional
    public Session removeSlot(Long sessionId, Long sectionId, Long slotId) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        var section = SessionSections.section(session, sectionId);
        var slot = SessionSections.slot(section, slotId);
        sessionAuthorizer.requireSlotOwnerOrManager(organizationId, session, slot, currentUser.userId());
        return dropSlot(organizationId, session, section, slotId);
    }

    private Session dropSlot(String organizationId, SessionRecord session, SectionRecord section, Long slotId) {

        var slot = SessionSections.slot(section, slotId);
        topicLifecycle.release(organizationId, slot.getTopicId());
        var slots = new ArrayList<>(SessionSections.nonNullSlots(section.getSlots()));
        slots.removeIf(s -> slotId.equals(s.getId()));
        var saved = sessionStore.save(
                SessionSections.replaceSection(session, section.getId(), section.toBuilder().slots(slots).build()));
        eventPublisher.publishEvent(new SlotCleared(slotId, session.getCommunityId()));
        return sessionMapper.toSession(saved);
    }

    @Transactional
    public Session withdraw(Long sessionId, Long sectionId, Long slotId) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        return removePresenterFromSlot(organizationId, session, new SlotLocation(sectionId, slotId),
                currentUser.userId());
    }

    @Transactional
    public Session removePresenter(Long sessionId, Long sectionId, Long slotId, String userId) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        var section = SessionSections.section(session, sectionId);
        var slot = SessionSections.slot(section, slotId);
        sessionAuthorizer.requireSlotOwnerOrManager(organizationId, session, slot, currentUser.userId());
        return removePresenterFromSlot(organizationId, session, new SlotLocation(sectionId, slotId), userId);
    }

    /**
     * Assigns a free-text presenter reference to an existing slot, e.g. when a presenter is discovered after the slot
     * was created, or an organizer corrects a past session by hand. Mirrors {@link #removePresenter}'s authorization
     * floor. A reference that does not resolve to exactly one member leaves the slot untouched and reports why, rather
     * than guessing.
     */
    @Override
    @Transactional
    public PresenterAssignment addPresenter(Long sessionId, Long sectionId, Long slotId, String presenter) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        requireContributable(session);
        var section = SessionSections.section(session, sectionId);
        var slot = SessionSections.slot(section, slotId);
        sessionAuthorizer.requireSlotOwnerOrManager(organizationId, session, slot, currentUser.userId());

        var communityId = session.getCommunityId();
        var target = new PresenterAssignmentTarget(organizationId, communityId, session, section, slot, sectionId,
                slotId);
        return switch (communityAccess.resolveMember(communityId, presenter)) {
            case MemberMatch.Matched m -> assignPresenter(target, m.userId());
            // The count the directory already computed is reported, not thrown away: a caller told only "ambiguous"
            // cannot tell two members who genuinely share a name from a reference that was simply too vague.
            case MemberMatch.Ambiguous ambiguous -> new PresenterAssignment(sessionMapper.toSession(session), false,
                    PresenterAssignment.AMBIGUOUS, ambiguous.candidates());
            case MemberMatch.NotFound ignored ->
                new PresenterAssignment(sessionMapper.toSession(session), false, PresenterAssignment.NOT_FOUND, 0);
        };
    }

    /** Groups the state {@link #assignPresenter} needs, to stay under the parameter-count limit. */
    private record PresenterAssignmentTarget(String organizationId, long communityId, SessionRecord session,
            SectionRecord section, SlotRecord slot, Long sectionId, Long slotId) {
    }

    /**
     * Assigns a resolved member to a slot, keyed by user id so re-assigning an already-present presenter is a no-op.
     */
    private PresenterAssignment assignPresenter(PresenterAssignmentTarget target, String userId) {

        var byUserId = SessionSections.presenters(target.slot()).stream()
                .collect(Collectors.toMap(SlotPresenter::getUserId, p -> p, (a, b) -> a));
        byUserId.putIfAbsent(userId, SlotPresenter.builder().userId(userId).build());
        var presenters = new HashSet<>(byUserId.values());
        var updatedSection = SessionSections.replaceSlot(target.section(), target.slotId(),
                target.slot().toBuilder().presenters(presenters).build());
        var saved = sessionStore
                .save(SessionSections.replaceSection(target.session(), target.sectionId(), updatedSection));
        var presenterIds = presenters.stream().map(SlotPresenter::getUserId).toList();
        slotEventPublisher.publish(new SlotEventCoords(target.organizationId(), target.communityId(), target.slotId()),
                presenterIds, target.slot().getTopicId(), SessionSections.tags(target.slot()));
        return new PresenterAssignment(sessionMapper.toSession(saved), true, null, 0);
    }

    private record SlotLocation(Long sectionId, Long slotId) {
    }

    private Session removePresenterFromSlot(String organizationId, SessionRecord session, SlotLocation loc,
            String userId) {

        var section = SessionSections.section(session, loc.sectionId());
        var slot = SessionSections.slot(section, loc.slotId());
        var presenters = new HashSet<>(SessionSections.presenters(slot));
        presenters.removeIf(p -> p.getUserId().equals(userId));

        var updatedSection = SessionSections.replaceSlot(section, loc.slotId(),
                slot.toBuilder().presenters(presenters).build());
        var saved = sessionStore.save(SessionSections.replaceSection(session, loc.sectionId(), updatedSection));
        var presenterIds = presenters.stream().map(SlotPresenter::getUserId).toList();
        slotEventPublisher.publish(new SlotEventCoords(organizationId, session.getCommunityId(), loc.slotId()),
                presenterIds, slot.getTopicId(), SessionSections.tags(slot));
        return sessionMapper.toSession(saved);
    }

    private void requireContributable(SessionRecord session) {

        if ("CANCELLED".equals(session.getStatus())) {
            throw new ConflictException("SESSION_CANCELLED", "This session is cancelled");
        }
        if ("SKIPPED".equals(session.getStatus())) {
            throw new ConflictException("SESSION_SKIPPED", "This session has been skipped");
        }
    }

    /**
     * What {@link #attachTopic} needs to decide whether a topic write reaches the schedule: the organization and
     * community the slot's session lives in, the topic being attached ({@code null} to detach), what the slot already
     * carried before this write, and the caller making it. Groups five inputs under the parameter-count limit the same
     * way {@link PresenterAssignmentTarget} groups {@link #assignPresenter}'s.
     */
    private record TopicAttachment(String organizationId, Long communityId, Long topicId, Long previousTopicId,
            String userId) {
    }

    /**
     * {@code previousTopicId} is what the slot already carried before this write - always {@code null} at
     * {@link #addSlot}'s one call site, since a new slot never has a prior topic. The queue-topics policy is checked
     * only when {@code topicId} actually changes what the slot is attached to: an edit that re-sends the same,
     * already-attached topic - the title, the notes, an unrelated field - is not a new act of queueing anything, and
     * gating it the same way {@link #queueTopic} gates a genuine attachment would lock a slot's owner out of editing
     * their own slot the moment the policy tightens or an organizer hands them a slot they cannot otherwise queue into.
     */
    private Long attachTopic(TopicAttachment attachment) {

        if (attachment.topicId() == null) {
            return null;
        }
        if (!attachment.topicId().equals(attachment.previousTopicId())) {
            requireMayQueueTopics(attachment.communityId(), attachment.userId());
        }
        topicLifecycle.schedule(attachment.organizationId(), attachment.communityId(), attachment.topicId());
        return attachment.topicId();
    }

    /**
     * Whether this caller may move a backlog topic onto the schedule. {@code queueTopic} is the endpoint named after
     * the act, but {@code addSlot} and {@code editSlot} reach the same transition by carrying a {@code topicId} on
     * their own request, so the rule lives here rather than at either entry point.
     */
    private void requireMayQueueTopics(Long communityId, String userId) {

        if (communityAccess.isOrganizer(communityId, userId)) {
            return;
        }
        if (communityAccess.contributionPolicy(communityId).canQueueTopics()
                && communityAccess.isMember(communityId, userId)) {
            return;
        }
        throw new ForbiddenException("Only an organizer may queue a topic");
    }

    private SessionRecord findOrThrow(String organizationId, Long id) {

        return sessionStore.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Session not found: " + id));
    }

    private void requireMember(long communityId) {

        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
    }
}
