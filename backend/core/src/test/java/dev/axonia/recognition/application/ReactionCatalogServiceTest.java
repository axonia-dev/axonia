package dev.axonia.recognition.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.CustomReactionStore;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.domain.CustomReaction;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.paid.PaidAction;
import dev.axonia.shared.api.paid.PaidCapability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactionCatalogServiceTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    private CustomReactionStore store;
    private OrganizationContext organizationContext;
    private CurrentUser currentUser;
    private AuthorizationGate gate;
    private PaidCapability paidCapability;
    private ReactionCatalogService service;

    @BeforeEach
    void setUp() {
        store = mock(CustomReactionStore.class);
        organizationContext = mock(OrganizationContext.class);
        currentUser = mock(CurrentUser.class);
        gate = mock(AuthorizationGate.class);
        paidCapability = mock(PaidCapability.class);
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(USER);
        when(store.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new ReactionCatalogService(store, organizationContext, currentUser, gate, paidCapability,
                new MediaLinks("https://media.example.test"));
    }

    @Test
    void aProposalFromSomebodyEntitledLandsPending() {

        givenEntitled();

        var proposed = service.propose("yak-shaving", "key");

        assertThat(proposed.pending()).isTrue();
        assertThat(proposed.name()).isEqualTo("yak-shaving");
        assertThat(proposed.proposedByUserId()).isEqualTo(USER);
    }

    @Test
    void aProposalFromAWritePermissionHolderIsApprovedOnArrival() {

        givenEntitled();
        when(gate.hasPermission(Permission.REACTION_WRITE)).thenReturn(true);

        var proposed = service.propose("ship-it", "key");

        assertThat(proposed.pending()).isFalse();
        assertThat(proposed.proposedByUserId()).isNull();
    }

    @Test
    void aDefinitionsImageAddressIsMintedFromItsKey() {

        // given
        when(gate.hasPermission(Permission.REACTION_WRITE)).thenReturn(true);

        // when
        var actual = service.propose("ship-it", "reactions/default/ship.png");

        // then
        assertThat(actual.imageUrl()).isEqualTo("https://media.example.test/reactions/default/ship.png");
    }

    @Test
    void aFreeMemberCannotPropose() {

        when(paidCapability.isOpenToCaller()).thenReturn(false);
        when(gate.hasPermission(Permission.REACTION_WRITE)).thenReturn(false);
        when(paidCapability.refusal(PaidAction.PROPOSE_REACTION)).thenReturn("Not open.");

        assertThatThrownBy(() -> service.propose("nope", "key")).isInstanceOf(ForbiddenException.class)
                .hasMessage("Not open.");
    }

    /**
     * The web edge asks this before it spends an upload, so a refused caller costs no write to object storage. It is
     * the same refusal {@code propose} makes on its own, asked earlier.
     */
    @Test
    void requireMayProposeRefusesAFreeMemberBeforeAnythingIsSpent() {

        when(paidCapability.isOpenToCaller()).thenReturn(false);
        when(gate.hasPermission(Permission.REACTION_WRITE)).thenReturn(false);
        when(paidCapability.refusal(PaidAction.PROPOSE_REACTION)).thenReturn("Not open.");

        assertThatThrownBy(() -> service.requireMayPropose()).isInstanceOf(ForbiddenException.class)
                .hasMessage("Not open.");
    }

    @Test
    void requireMayProposePassesForSomebodyEntitled() {

        givenEntitled();

        service.requireMayPropose();
    }

    @Test
    void requireMayProposePassesForAWritePermissionHolder() {

        when(gate.hasPermission(Permission.REACTION_WRITE)).thenReturn(true);

        service.requireMayPropose();
    }

    @Test
    void aNameAlreadyTakenIsRefusedWhateverStateHoldsIt() {

        givenEntitled();
        when(store.nameTaken(ORG, "ship-it")).thenReturn(true);

        assertThatThrownBy(() -> service.propose("ship-it", "key")).isInstanceOf(ConflictException.class);
    }

    @Test
    void theOrganizationCapIsAHardStop() {

        givenEntitled();
        when(store.countInState(ORG, CustomReaction.APPROVED)).thenReturn(60);

        assertThatThrownBy(() -> service.propose("one-too-many", "key")).isInstanceOf(ConflictException.class)
                .hasMessageContaining("60");
    }

    @Test
    void aPersonMayHoldOnlyThreeOpenProposals() {

        givenEntitled();
        when(store.countProposedBy(ORG, CustomReaction.PENDING, USER)).thenReturn(3);

        assertThatThrownBy(() -> service.propose("fourth", "key")).isInstanceOf(ConflictException.class);
    }

    @Test
    void aNameIsBounded() {

        givenEntitled();

        assertThatThrownBy(() -> service.propose("a", "key")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.propose("x".repeat(33), "key")).isInstanceOf(ValidationException.class);
    }

    @Test
    void aMissingNameIsRejectedRatherThanBlowingUpOnTrim() {

        givenEntitled();

        assertThatThrownBy(() -> service.propose(null, "key")).isInstanceOf(ValidationException.class);
    }

    @Test
    void eligibilityReportsTheHeadroomThePickerDraws() {

        givenEntitled();
        when(store.countProposedBy(ORG, CustomReaction.PENDING, USER)).thenReturn(1);

        var eligibility = service.eligibility();

        assertThat(eligibility.mayPropose()).isTrue();
        assertThat(eligibility.openProposals()).isEqualTo(1);
        assertThat(eligibility.openProposalLimit()).isEqualTo(3);
        // The administration queue draws "n of this" rather than keeping its own copy of the ceiling.
        assertThat(eligibility.approvedLimit()).isEqualTo(ReactionCatalogService.MAX_APPROVED);
    }

    @Test
    void eligibilityReportsNoHeadroomForAFreeMember() {

        when(paidCapability.isOpenToCaller()).thenReturn(false);

        assertThat(service.eligibility().mayPropose()).isFalse();
    }

    @Test
    void eligibilityOffersTheAffordanceToAWriteHolderEvenWithoutThePaidCapability() {

        when(gate.hasPermission(Permission.REACTION_WRITE)).thenReturn(true);
        // Never reached: write short-circuits mayPropose() before the paid axis is consulted at all. Stubbed
        // anyway, lenient, to make the "even without the paid capability" premise explicit in the test itself.
        lenient().when(paidCapability.isOpenToCaller()).thenReturn(false);

        assertThat(service.eligibility().mayPropose()).isTrue();
    }

    @Test
    void approvedListsOnlyTheApprovedOnesAndDropsTheProposer() {

        var entity = aReaction(1L, CustomReaction.APPROVED, "high-five", "reactions/org-1/hf.png", USER);
        when(store.inState(ORG, CustomReaction.APPROVED)).thenReturn(List.of(entity));

        var result = service.approved();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pending()).isFalse();
        assertThat(result.get(0).proposedByUserId()).isNull();
    }

    @Test
    void allRequiresReadPermissionAndKeepsTheProposerOnPendingRows() {

        var pending = aReaction(2L, CustomReaction.PENDING, "clapping", "reactions/org-1/cl.png", USER);
        when(store.all(ORG)).thenReturn(List.of(pending));

        var result = service.all();

        verify(gate).requirePermission(Permission.REACTION_READ);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).pending()).isTrue();
        assertThat(result.get(0).proposedByUserId()).isEqualTo(USER);
    }

    @Test
    void allPopulatesTheUsageCountFromTheJoinQuery() {

        var approved = aReaction(9L, CustomReaction.APPROVED, "high-five", "reactions/org-1/hf.png", USER);
        when(store.all(ORG)).thenReturn(List.of(approved));
        when(store.usageByDefinition(ORG)).thenReturn(Map.of(9L, 48L));

        var result = service.all();

        assertThat(result.get(0).usageCount()).isEqualTo(48L);
    }

    @Test
    void allDefaultsUsageToZeroForADefinitionTheJoinDidNotReturn() {

        var approved = aReaction(10L, CustomReaction.APPROVED, "clapping", "reactions/org-1/cl.png", USER);
        when(store.all(ORG)).thenReturn(List.of(approved));
        when(store.usageByDefinition(ORG)).thenReturn(Map.of());

        var result = service.all();

        assertThat(result.get(0).usageCount()).isZero();
    }

    @Test
    void approvedNeverPaysForTheUsageCount() {

        var entity = aReaction(11L, CustomReaction.APPROVED, "high-five", "reactions/org-1/hf.png", USER);
        when(store.inState(ORG, CustomReaction.APPROVED)).thenReturn(List.of(entity));

        var result = service.approved();

        assertThat(result.get(0).usageCount()).isZero();
        verify(store, never()).usageByDefinition(any());
    }

    /**
     * The proposer's own view. It keeps the proposer on the row - it is the caller's own id, and the picker's
     * {@code approved()} is the read that must not carry it - and it never asks for {@code reaction:read}.
     */
    @Test
    void mineReturnsTheCallersOwnWaitingProposalsWithoutTheAdministrationPermission() {

        var own = aReaction(12L, CustomReaction.PENDING, "yak-shaving", "reactions/org-1/yak.png", USER);
        when(store.proposedBy(ORG, CustomReaction.PENDING, USER)).thenReturn(List.of(own));

        var result = service.mine();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pending()).isTrue();
        assertThat(result.get(0).proposedByUserId()).isEqualTo(USER);
        assertThat(result.get(0).usageCount()).isZero();
        verify(gate, never()).requirePermission(Permission.REACTION_READ);
    }

    /** Absence is a real answer here: a member with nothing waiting is what the screen draws nothing for. */
    @Test
    void mineIsEmptyForAMemberWithNothingWaiting() {

        when(store.proposedBy(ORG, CustomReaction.PENDING, USER)).thenReturn(List.of());

        assertThat(service.mine()).isEmpty();
    }

    @Test
    void approvingAPendingProposalRequiresWritePermission() {

        var pending = aReaction(3L, CustomReaction.PENDING, "clapping", "reactions/org-1/cl.png", "other-user");
        when(store.find(ORG, 3L)).thenReturn(Optional.of(pending));

        var approved = service.approve(3L);

        verify(gate).requirePermission(Permission.REACTION_WRITE);
        assertThat(approved.pending()).isFalse();
    }

    @Test
    void approvingAnAlreadyApprovedReactionIsANoOp() {

        var alreadyApproved = aReaction(4L, CustomReaction.APPROVED, "clapping", "reactions/org-1/cl.png", USER);
        when(store.find(ORG, 4L)).thenReturn(Optional.of(alreadyApproved));

        var result = service.approve(4L);

        assertThat(result.pending()).isFalse();
        verify(store, never()).save(any());
    }

    @Test
    void approvingIntoAFullCatalogIsRefused() {

        var pending = aReaction(5L, CustomReaction.PENDING, "clapping", "reactions/org-1/cl.png", USER);
        when(store.find(ORG, 5L)).thenReturn(Optional.of(pending));
        when(store.countInState(ORG, CustomReaction.APPROVED)).thenReturn(60);

        assertThatThrownBy(() -> service.approve(5L)).isInstanceOf(ConflictException.class).hasMessageContaining("60");
    }

    @Test
    void approvingAnUnknownIdIsNotFound() {

        when(store.find(ORG, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void withdrawingOnesOwnPendingProposalNeedsNoPermission() {

        var own = aReaction(6L, CustomReaction.PENDING, "clapping", "reactions/org-1/cl.png", USER);
        when(store.find(ORG, 6L)).thenReturn(Optional.of(own));

        var key = service.remove(6L);

        assertThat(key).isEqualTo("reactions/org-1/cl.png");
        verify(gate, never()).requirePermission(Permission.REACTION_WRITE);
        verify(store).remove(own);
    }

    @Test
    void removingSomebodyElsesPendingProposalRequiresWritePermission() {

        var someoneElses = aReaction(7L, CustomReaction.PENDING, "clapping", "reactions/org-1/cl.png", "other-user");
        when(store.find(ORG, 7L)).thenReturn(Optional.of(someoneElses));

        service.remove(7L);

        verify(gate).requirePermission(Permission.REACTION_WRITE);
    }

    @Test
    void removingAnApprovedReactionRequiresWritePermission() {

        var approved = aReaction(8L, CustomReaction.APPROVED, "clapping", "reactions/org-1/cl.png", USER);
        when(store.find(ORG, 8L)).thenReturn(Optional.of(approved));

        var key = service.remove(8L);

        assertThat(key).isEqualTo("reactions/org-1/cl.png");
        verify(gate).requirePermission(Permission.REACTION_WRITE);
    }

    @Test
    void removingAnUnknownIdIsNotFound() {

        when(store.find(ORG, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(100L)).isInstanceOf(NotFoundException.class);
    }

    private void givenEntitled() {
        when(paidCapability.isOpenToCaller()).thenReturn(true);
    }

    private static CustomReaction aReaction(Long id, String state, String name, String imageKey,
            String proposedByUserId) {

        return CustomReaction.builder().id(id).organizationId(ORG).name(name).imageKey(imageKey).state(state)
                .proposedByUserId(proposedByUserId).build();
    }
}
