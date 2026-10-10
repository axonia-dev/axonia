package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.community.api.TopicManagementPolicy;
import dev.axonia.events.api.ActionItem;
import dev.axonia.events.api.ActionItemClosed;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.ActionItemReopened;
import dev.axonia.events.api.RaiseActionItemRequest;
import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ActionItemServiceTest {

    private static final String SLUG = "backend-guild";
    private static final String BOB = "bob";

    @Mock
    private ActionItemStore actionItemStore;

    @Mock
    private ActionItemCheckpointStore actionItemCheckpointStore;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private UserProfileAccess userProfileAccess;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private SeriesStore seriesStore;

    private ActionItemService sut;

    /** A fake, in-memory action_item table: the tests chain raise -> close/drop -> reopen on the same row. */
    private final Map<Long, ActionItemRecord> store = new HashMap<>();
    // Offset so a generated item id can never equal the community id (1L): an assertion that the published event
    // carries the item id would otherwise pass against a publisher sending the community id instead.
    private final AtomicLong idSeq = new AtomicLong(100);

    private final long otherCommunitySessionId = 77L;

    // Offsets from the clock, never a literal date: a pinned checkpoint has to be upcoming when the test runs.
    private static Instant upcoming() {

        return Instant.now().plus(30, ChronoUnit.DAYS);
    }

    @BeforeEach
    void buildSut() {

        var checkpointResolver = new CheckpointResolver(sessionStore, seriesStore, Clock.systemUTC());
        var topicAuthorizer = new TopicAuthorizer(communityAccess);
        var actionItemMapper = new ActionItemMapper();
        var actionItemReadModel = new ActionItemReadModel(actionItemCheckpointStore, checkpointResolver,
                userProfileAccess, topicAuthorizer, actionItemMapper, currentUser, actionItemStore);
        var topicMemberResolver = new TopicMemberResolver(communityAccess);
        var sessionCommunityGuard = new SessionCommunityGuard(sessionStore, Clock.systemUTC());
        var actionItemCloser = new ActionItemCloser(actionItemStore, actionItemMapper, eventPublisher);
        sut = new ActionItemService(actionItemStore, actionItemReadModel, actionItemCloser, communityAccess,
                currentUser, organizationContext, eventPublisher, topicMemberResolver, sessionCommunityGuard,
                topicAuthorizer, sessionStore, Clock.systemUTC());
    }

    @BeforeEach
    void seedSeams() {

        when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("alice");
        lenient().when(communityAccess.communityIdForSlug(SLUG)).thenReturn(1L);
        lenient().when(communityAccess.isMember(anyLong(), any())).thenReturn(true);
        lenient().when(communityAccess.topicManagementPolicy(anyLong())).thenReturn(TopicManagementPolicy.ANY_MEMBER);
        lenient().when(communityAccess.resolveMember(1L, BOB)).thenReturn(new MemberMatch.Matched(BOB));
        lenient().when(userProfileAccess.displayName(any(), any())).thenReturn("Test User");
    }

    @BeforeEach
    void fakeRepository() {

        store.clear();
        lenient().when(actionItemStore.save(any())).thenAnswer(inv -> {
            ActionItemRecord e = inv.getArgument(0);
            if (e.getId() == null) {
                e = e.toBuilder().id(idSeq.incrementAndGet()).build();
            }
            store.put(e.getId(), e);
            return e;
        });
        lenient().when(actionItemStore.findByOrganizationIdAndId(any(), any())).thenAnswer(inv -> {
            String organizationId = inv.getArgument(0);
            Long id = inv.getArgument(1);
            var found = store.get(id);
            return found != null && found.getOrganizationId().equals(organizationId)
                    ? Optional.of(found)
                    : Optional.empty();
        });
    }

    @Test
    void aRaisedItemIsOpenAndOwesTheNextSession() {

        var upcoming = SessionRecordMother.any().id(9L).communityId(1L)
                .scheduledAt(Instant.parse("2026-10-01T18:00:00Z")).build();
        lenient().when(sessionStore.findSoonestUpcoming(any(), anyLong(), any())).thenReturn(Optional.of(upcoming));

        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Close the Jira ticket").assignee(BOB).build());

        assertThat(item.outcome()).isEqualTo(ActionItemOutcome.OPEN);
        assertThat(item.checkpoint().pinned()).isFalse();
        assertThat(item.checkpoint().sessionId()).isEqualTo(9L);
        assertThat(item.carryCount()).isZero();
    }

    @Test
    void closingCreditsTheAssignee() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci")).build());

        sut.close(item.id());

        var captor = ArgumentCaptor.forClass(ActionItemClosed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().assigneeUserId()).isEqualTo(BOB);
        assertThat(captor.getValue().actionItemId()).isEqualTo(item.id());
        // Empty tags make the expertise listener delete the signal source instead of crediting it.
        assertThat(captor.getValue().tags()).containsExactly("ci");
    }

    @Test
    void droppingClosesWithoutCredit() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Review the old proxy").assignee(BOB)
                .tags(List.of("infra")).build());

        var dropped = sut.drop(item.id());

        assertThat(dropped.outcome()).isEqualTo(ActionItemOutcome.DROPPED);
        verify(eventPublisher, never()).publishEvent(any(ActionItemClosed.class));
    }

    /**
     * A late-bound item reached its session only by floating to it, and settling stops it floating. Without a pin taken
     * at that moment it would leave the very list it was settled from, so the press could not be undone where it
     * happened - the whole reason the settled row stays on show.
     */
    @Test
    void settlingALateBoundItemPinsItToTheSessionItWasAnsweredAt() {

        var upcoming = SessionRecordMother.any().id(9L).communityId(1L)
                .scheduledAt(Instant.parse("2026-10-01T18:00:00Z")).build();
        when(sessionStore.findSoonestUpcoming(any(), anyLong(), any())).thenReturn(Optional.of(upcoming));
        when(sessionStore.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.of(upcoming));
        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci")).build());
        assertThat(item.checkpoint().pinned()).isFalse();

        var closed = sut.close(item.id());

        assertThat(closed.checkpoint().sessionId()).isEqualTo(9L);
        assertThat(closed.checkpoint().pinned()).isTrue();
    }

    /** The same pin is taken when the settlement is a drop, since either way the item stops floating. */
    @Test
    void droppingALateBoundItemPinsItToo() {

        var upcoming = SessionRecordMother.any().id(9L).communityId(1L)
                .scheduledAt(Instant.parse("2026-10-01T18:00:00Z")).build();
        when(sessionStore.findSoonestUpcoming(any(), anyLong(), any())).thenReturn(Optional.of(upcoming));
        when(sessionStore.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.of(upcoming));
        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Review the old proxy").assignee(BOB).build());

        var dropped = sut.drop(item.id());

        assertThat(dropped.checkpoint().sessionId()).isEqualTo(9L);
        assertThat(dropped.checkpoint().pinned()).isTrue();
    }

    @Test
    void droppingADoneItemSettlesItAndWithdrawsTheCredit() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci")).build());
        sut.close(item.id());

        var dropped = sut.drop(item.id());

        assertThat(dropped.outcome()).isEqualTo(ActionItemOutcome.DROPPED);
        verify(eventPublisher).publishEvent(any(ActionItemReopened.class));
    }

    @Test
    void droppingAnOpenItemWithdrawsNothing() {

        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Review the old proxy").assignee(BOB).build());

        sut.drop(item.id());

        verify(eventPublisher, never()).publishEvent(any(ActionItemReopened.class));
    }

    @Test
    void reopeningADroppedItemWithdrawsNothing() {

        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Review the old proxy").assignee(BOB).build());
        sut.drop(item.id());

        var reopened = sut.reopen(item.id());

        assertThat(reopened.outcome()).isEqualTo(ActionItemOutcome.OPEN);
        assertThat(reopened.checkpoint().pinned()).isFalse();
        verify(eventPublisher, never()).publishEvent(any(ActionItemReopened.class));
    }

    @Test
    void reopeningAClosedItemWithdrawsTheCredit() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci")).build());
        sut.close(item.id());

        sut.reopen(item.id());

        verify(eventPublisher).publishEvent(any(ActionItemReopened.class));
    }

    /**
     * A manual reopen is an undo, and an undo that moves the item to a different session than the one it was pinned to
     * is not an undo. Carrying an item forward is what unpins it, and ActionItemLifecycle.decide's CARRIED arm clears
     * the pin itself.
     */
    @Test
    void reopeningKeepsThePinnedCheckpoint() {

        var pinnedSession = SessionRecordMother.any().id(42L).communityId(1L).scheduledAt(upcoming()).build();
        when(sessionStore.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.of(pinnedSession));
        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .checkpointSessionId(42L).build());
        assertThat(item.checkpoint().pinned()).isTrue();
        sut.close(item.id());

        var reopened = sut.reopen(item.id());

        assertThat(reopened.checkpoint().pinned()).isTrue();
        assertThat(reopened.checkpoint().sessionId()).isEqualTo(42L);
    }

    /**
     * {@code TopicMemberResolver.resolveAssignee}, reused as-is, refuses an unresolvable reference with
     * {@code ConflictException("ASSIGNEE_NOT_FOUND", ...)}, not {@code NotFoundException}.
     */
    @Test
    void anAssigneeWhoIsNotAMemberRefusesTheWrite() {

        when(communityAccess.resolveMember(1L, "nobody@example.com")).thenReturn(new MemberMatch.NotFound());

        assertThatThrownBy(() -> sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Anything").assignee("nobody@example.com").build()))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("ASSIGNEE_NOT_FOUND");
    }

    @Test
    void anAssigneeMatchingSeveralMembersRefusesTheWriteAndReportsTheCount() {

        when(communityAccess.resolveMember(1L, "bob")).thenReturn(new MemberMatch.Ambiguous(3));

        assertThatThrownBy(
                () -> sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee("bob").build()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("3 members").extracting("code")
                .isEqualTo("ASSIGNEE_AMBIGUOUS");
        verify(actionItemStore, never()).save(any());
    }

    @Test
    void raisingAsANonMemberRefusesTheWrite() {

        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        assertThatThrownBy(() -> sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").build()))
                .isInstanceOf(ForbiddenException.class);
        verify(actionItemStore, never()).save(any());
    }

    @Test
    void aCheckpointInAnotherCommunityRefusesTheWrite() {

        var otherSession = SessionRecordMother.any().id(otherCommunitySessionId).communityId(999L).build();
        when(sessionStore.findByOrganizationIdAndId("default", otherCommunitySessionId))
                .thenReturn(Optional.of(otherSession));

        assertThatThrownBy(
                () -> sut
                        .raise(SLUG,
                                RaiseActionItemRequest.builder().title("Anything")
                                        .checkpointSessionId(otherCommunitySessionId).build()))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * A checkpoint names where an item will be answered, so the session has to be able to answer it. A cancelled one
     * never will: the item is pinned, so {@code pinLateBound} skips it; {@code clearPinsFor} never fires for that
     * session again; and no follow-up pass lists it.
     */
    @Test
    void aCheckpointOnACancelledSessionIsRefused() {

        var cancelled = SessionRecordMother.any().id(43L).communityId(1L).status("CANCELLED").scheduledAt(upcoming())
                .build();
        when(sessionStore.findByOrganizationIdAndId("default", 43L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Anything").checkpointSessionId(43L).build()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("still upcoming");
    }

    @Test
    void aCheckpointOnASessionWhoseHourHasPassedIsRefused() {

        var past = SessionRecordMother.any().id(44L).communityId(1L)
                .scheduledAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(sessionStore.findByOrganizationIdAndId("default", 44L)).thenReturn(Optional.of(past));

        assertThatThrownBy(() -> sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Anything").checkpointSessionId(44L).build()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("still upcoming");
    }

    @Test
    void repinningAnItemToAnAlreadyHeldSessionIsRefused() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Original").assignee(BOB).build());
        var held = SessionRecordMother.any().id(45L).communityId(1L).status("HELD").scheduledAt(upcoming()).build();
        when(sessionStore.findByOrganizationIdAndId("default", 45L)).thenReturn(Optional.of(held));

        assertThatThrownBy(() -> sut.update(item.id(),
                UpdateActionItemRequest.builder().title("Original").checkpointSessionId(45L).build()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("still upcoming");
    }

    @Test
    void anOriginSessionInAnotherCommunityRefusesTheWrite() {

        var otherSession = SessionRecordMother.any().id(otherCommunitySessionId).communityId(999L).build();
        when(sessionStore.findByOrganizationIdAndId("default", otherCommunitySessionId))
                .thenReturn(Optional.of(otherSession));

        assertThatThrownBy(() -> sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Anything").originSessionId(otherCommunitySessionId).build()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void closingAnAlreadyClosedItemIsRefused() {

        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB).build());
        sut.close(item.id());

        assertThatThrownBy(() -> sut.close(item.id())).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ALREADY_CLOSED");
    }

    @Test
    void droppingAnAlreadyDroppedItemIsRefused() {

        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Review the old proxy").assignee(BOB).build());
        sut.drop(item.id());

        assertThatThrownBy(() -> sut.drop(item.id())).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ALREADY_DROPPED");
    }

    @Test
    void reopeningAnOpenItemIsRefused() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee(BOB).build());

        assertThatThrownBy(() -> sut.reopen(item.id())).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("NOT_CLOSED");
    }

    @Test
    void underOrganizerOnlyPolicyANonAssigneeNonOrganizerIsRefused() {

        when(communityAccess.topicManagementPolicy(1L)).thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB).build());

        assertThatThrownBy(() -> sut.close(item.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void underOrganizerOnlyPolicyAnOrganizerWhoIsNotTheAssigneeMaySucceed() {

        when(communityAccess.topicManagementPolicy(1L)).thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB).build());

        var closed = sut.close(item.id());

        assertThat(closed.outcome()).isEqualTo(ActionItemOutcome.DONE);
    }

    @Test
    void theAssigneeMayCloseTheirOwnItemWithoutManagementPermission() {

        // policy is ORGANIZER_ONLY and BOB is never stubbed as an organizer (defaults to false), so a DONE outcome
        // here can only come from the assignee shortcut, not from the management-permission check.
        when(communityAccess.topicManagementPolicy(1L)).thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB).build());
        when(currentUser.userId()).thenReturn(BOB);

        var closed = sut.close(item.id());

        assertThat(closed.outcome()).isEqualTo(ActionItemOutcome.DONE);
    }

    @Test
    void closingAnUnknownItemIsNotFound() {

        assertThatThrownBy(() -> sut.close(999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listDelegatesToTheReadModelScopedToTheCommunityCarryingEveryFilter() {

        var stored = ActionItemRecord.builder().id(5L).organizationId("default").communityId(1L).title("Anything")
                .description("").raisedByUserId(BOB).outcome(ActionItemOutcome.OPEN).build();
        when(actionItemStore.findFiltered(eq("default"), eq(1L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stored)));

        var page = sut.list(SLUG, new ActionItemQuery(ActionItemOutcome.OPEN, 7L, 9L), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItem::id).containsExactly(5L);
        verify(actionItemStore).findFiltered(eq("default"), eq(1L),
                eq(new ActionItemFilter(ActionItemOutcome.OPEN, 7L, 9L)), any());
    }

    @Test
    void updateAsAManagerAppliesTheNewFieldsAndRePinsTheCheckpoint() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Original").assignee(BOB).build());
        var newSession = SessionRecordMother.any().id(11L).communityId(1L).build();
        when(sessionStore.findByOrganizationIdAndId("default", 11L)).thenReturn(Optional.of(newSession));

        var updated = sut.update(item.id(), UpdateActionItemRequest.builder().title("Updated title")
                .description("Updated body").tags(List.of("kafka")).assignee(BOB).checkpointSessionId(11L).build());

        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.description()).isEqualTo("Updated body");
        assertThat(updated.tags()).containsExactly("kafka");
        assertThat(updated.checkpoint().sessionId()).isEqualTo(11L);
        assertThat(updated.checkpoint().pinned()).isTrue();
    }

    @Test
    void updateAsANonManagerIsForbidden() {

        when(communityAccess.topicManagementPolicy(1L)).thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee(BOB).build());

        assertThatThrownBy(() -> sut.update(item.id(), UpdateActionItemRequest.builder().title("New").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateWithACheckpointInAnotherCommunityRefusesTheWrite() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee(BOB).build());
        var otherSession = SessionRecordMother.any().id(otherCommunitySessionId).communityId(999L).build();
        when(sessionStore.findByOrganizationIdAndId("default", otherCommunitySessionId))
                .thenReturn(Optional.of(otherSession));

        assertThatThrownBy(
                () -> sut
                        .update(item.id(),
                                UpdateActionItemRequest.builder().title("Anything")
                                        .checkpointSessionId(otherCommunitySessionId).build()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateOfAnUnknownItemIsNotFound() {

        assertThatThrownBy(() -> sut.update(999L, UpdateActionItemRequest.builder().title("New").build()))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * {@code checkpointSessionId} is the session that answered a closed item, part of its historical record - a manager
     * rewriting it on a settled row would falsify that record, and re-tagging it could reopen the same expertise-credit
     * gaming path {@code update} is manager-only to close off, this time by editing the tags after the fact rather than
     * before closing. So a closed item refuses {@code update} outright: reopen, edit, close.
     */
    @Test
    void updateOfAClosedItemIsRefused() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci")).build());
        sut.close(item.id());

        assertThatThrownBy(() -> sut.update(item.id(), UpdateActionItemRequest.builder().title("New").build()))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("NOT_OPEN");
    }

    @Test
    void updateOfADroppedItemIsRefused() {

        var item = sut.raise(SLUG,
                RaiseActionItemRequest.builder().title("Review the old proxy").assignee(BOB).build());
        sut.drop(item.id());

        assertThatThrownBy(() -> sut.update(item.id(), UpdateActionItemRequest.builder().title("New").build()))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("NOT_OPEN");
    }

    @Test
    void updateOfAnOpenItemIsAllowed() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Original").assignee(BOB).build());

        var updated = sut.update(item.id(), UpdateActionItemRequest.builder().title("Updated title").build());

        assertThat(updated.title()).isEqualTo("Updated title");
    }

    /**
     * The edit form sends no {@code tags} field at all, so an absent one has to mean unchanged: the tags an item
     * carries at closing time are exactly what the expertise graph credits the assignee for, and reading the absence as
     * "none" would make a title typo fix destroy that credit.
     */
    @Test
    void updateWithoutTagsKeepsTheTagsTheItemAlreadyCarries() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci", "jenkins")).build());

        var updated = sut.update(item.id(),
                UpdateActionItemRequest.builder().title("Audit the CI pipelines").assignee(BOB).build());

        assertThat(updated.tags()).containsExactly("ci", "jenkins");
        sut.close(item.id());
        var captor = ArgumentCaptor.forClass(ActionItemClosed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tags()).containsExactly("ci", "jenkins");
    }

    @Test
    void updateWithAnEmptyTagListClearsTheTags() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Audit the CI pipeline").assignee(BOB)
                .tags(List.of("ci")).build());

        var updated = sut.update(item.id(),
                UpdateActionItemRequest.builder().title("Audit the CI pipeline").tags(List.of()).assignee(BOB).build());

        assertThat(updated.tags()).isEmpty();
    }

    /**
     * {@code SessionService.close} pins every late-bound open item to the session it just held, so the stored
     * checkpoint of a perfectly ordinary item routinely names a session in the past. Editing anything else about that
     * item must not be refused for a checkpoint the editor never touched.
     */
    @Test
    void editingAnItemPinnedToAnAlreadyHeldSessionLeavesTheCheckpointAlone() {

        seedPinnedToHeldSession();

        var updated = sut.update(5L,
                UpdateActionItemRequest.builder().title("Updated title").checkpointSessionId(46L).build());

        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.checkpoint().sessionId()).isEqualTo(46L);
        assertThat(updated.checkpoint().pinned()).isTrue();
    }

    @Test
    void movingTheCheckpointOfAPinnedItemToAnotherHeldSessionIsStillRefused() {

        seedPinnedToHeldSession();
        var alsoHeld = SessionRecordMother.any().id(47L).communityId(1L).status("HELD").scheduledAt(upcoming()).build();
        when(sessionStore.findByOrganizationIdAndId("default", 47L)).thenReturn(Optional.of(alsoHeld));

        assertThatThrownBy(() -> sut.update(5L,
                UpdateActionItemRequest.builder().title("Updated title").checkpointSessionId(47L).build()))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKPOINT_NOT_UPCOMING");
    }

    @Test
    void movingTheCheckpointOfAPinnedItemToAnUpcomingSessionSucceeds() {

        seedPinnedToHeldSession();
        var next = SessionRecordMother.any().id(48L).communityId(1L).scheduledAt(upcoming()).build();
        when(sessionStore.findByOrganizationIdAndId("default", 48L)).thenReturn(Optional.of(next));

        var updated = sut.update(5L,
                UpdateActionItemRequest.builder().title("Updated title").checkpointSessionId(48L).build());

        assertThat(updated.checkpoint().sessionId()).isEqualTo(48L);
        assertThat(updated.checkpoint().pinned()).isTrue();
    }

    /** What {@code pinLateBound} leaves behind once its session is held: an open item pinned to a past session. */
    private void seedPinnedToHeldSession() {

        var held = SessionRecordMother.any().id(46L).communityId(1L).status("HELD")
                .scheduledAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        lenient().when(sessionStore.findByOrganizationIdAndId("default", 46L)).thenReturn(Optional.of(held));
        store.put(5L,
                ActionItemRecord.builder().id(5L).organizationId("default").communityId(1L).title("Original")
                        .description("").raisedByUserId(BOB).assigneeUserId(BOB).outcome(ActionItemOutcome.OPEN)
                        .checkpointSessionId(46L).build());
    }

    @Test
    void removeAnOpenItemAsAManagerDeletesIt() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee(BOB).build());

        sut.remove(item.id());

        verify(actionItemStore).delete(any());
    }

    @Test
    void removeAClosedItemIsRefused() {

        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee(BOB).build());
        sut.close(item.id());

        assertThatThrownBy(() -> sut.remove(item.id())).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("NOT_OPEN");
    }

    @Test
    void removeAsANonManagerIsForbidden() {

        when(communityAccess.topicManagementPolicy(1L)).thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        var item = sut.raise(SLUG, RaiseActionItemRequest.builder().title("Anything").assignee(BOB).build());

        assertThatThrownBy(() -> sut.remove(item.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeOfAnUnknownItemIsNotFound() {

        assertThatThrownBy(() -> sut.remove(999L)).isInstanceOf(NotFoundException.class);
    }
}
