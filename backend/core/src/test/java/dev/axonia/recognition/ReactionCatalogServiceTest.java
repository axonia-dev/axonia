package dev.axonia.recognition;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.PaidAction;
import dev.axonia.PaidCapability;
import dev.axonia.ValidationException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.Permission;
import dev.axonia.media.MediaLinks;
import dev.axonia.recognition.internal.ReactionDefinitionEntity;
import dev.axonia.recognition.internal.ReactionDefinitionRepository;

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

    private ReactionDefinitionRepository repository;
    private OrganizationContext organizationContext;
    private CurrentUser currentUser;
    private AuthorizationGate gate;
    private PaidCapability paidCapability;
    private ReactionCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReactionDefinitionRepository.class);
        organizationContext = mock(OrganizationContext.class);
        currentUser = mock(CurrentUser.class);
        gate = mock(AuthorizationGate.class);
        paidCapability = mock(PaidCapability.class);
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(USER);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new ReactionCatalogService(repository, organizationContext, currentUser, gate, paidCapability,
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
        when(repository.existsByOrganizationIdAndName(ORG, "ship-it")).thenReturn(true);

        assertThatThrownBy(() -> service.propose("ship-it", "key")).isInstanceOf(ConflictException.class);
    }

    @Test
    void theOrganizationCapIsAHardStop() {

        givenEntitled();
        when(repository.countByOrganizationIdAndState(ORG, ReactionDefinitionEntity.APPROVED)).thenReturn(60);

        assertThatThrownBy(() -> service.propose("one-too-many", "key")).isInstanceOf(ConflictException.class)
                .hasMessageContaining("60");
    }

    @Test
    void aPersonMayHoldOnlyThreeOpenProposals() {

        givenEntitled();
        when(repository.countByOrganizationIdAndStateAndProposedByUserId(ORG, ReactionDefinitionEntity.PENDING, USER))
                .thenReturn(3);

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
        when(repository.countByOrganizationIdAndStateAndProposedByUserId(ORG, ReactionDefinitionEntity.PENDING, USER))
                .thenReturn(1);

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

        var entity = anEntity(1L, ReactionDefinitionEntity.APPROVED, "high-five", "reactions/org-1/hf.png", USER);
        when(repository.findByOrganizationIdAndStateOrderByNameAsc(ORG, ReactionDefinitionEntity.APPROVED))
                .thenReturn(List.of(entity));

        var result = service.approved();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pending()).isFalse();
        assertThat(result.get(0).proposedByUserId()).isNull();
    }

    @Test
    void allRequiresReadPermissionAndKeepsTheProposerOnPendingRows() {

        var pending = anEntity(2L, ReactionDefinitionEntity.PENDING, "clapping", "reactions/org-1/cl.png", USER);
        when(repository.findByOrganizationIdOrderByNameAsc(ORG)).thenReturn(List.of(pending));

        var result = service.all();

        verify(gate).requirePermission(Permission.REACTION_READ);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).pending()).isTrue();
        assertThat(result.get(0).proposedByUserId()).isEqualTo(USER);
    }

    @Test
    void allPopulatesTheUsageCountFromTheJoinQuery() {

        var approved = anEntity(9L, ReactionDefinitionEntity.APPROVED, "high-five", "reactions/org-1/hf.png", USER);
        when(repository.findByOrganizationIdOrderByNameAsc(ORG)).thenReturn(List.of(approved));
        when(repository.countUsageByDefinition(ORG))
                .thenReturn(List.of(new ReactionDefinitionRepository.DefinitionUsage(9L, 48L)));

        var result = service.all();

        assertThat(result.get(0).usageCount()).isEqualTo(48L);
    }

    @Test
    void allDefaultsUsageToZeroForADefinitionTheJoinDidNotReturn() {

        var approved = anEntity(10L, ReactionDefinitionEntity.APPROVED, "clapping", "reactions/org-1/cl.png", USER);
        when(repository.findByOrganizationIdOrderByNameAsc(ORG)).thenReturn(List.of(approved));
        when(repository.countUsageByDefinition(ORG)).thenReturn(List.of());

        var result = service.all();

        assertThat(result.get(0).usageCount()).isZero();
    }

    @Test
    void approvedNeverPaysForTheUsageCount() {

        var entity = anEntity(11L, ReactionDefinitionEntity.APPROVED, "high-five", "reactions/org-1/hf.png", USER);
        when(repository.findByOrganizationIdAndStateOrderByNameAsc(ORG, ReactionDefinitionEntity.APPROVED))
                .thenReturn(List.of(entity));

        var result = service.approved();

        assertThat(result.get(0).usageCount()).isZero();
        verify(repository, never()).countUsageByDefinition(any());
    }

    /**
     * The proposer's own view. It keeps the proposer on the row - it is the caller's own id, and the picker's
     * {@code approved()} is the read that must not carry it - and it never asks for {@code reaction:read}.
     */
    @Test
    void mineReturnsTheCallersOwnWaitingProposalsWithoutTheAdministrationPermission() {

        var own = anEntity(12L, ReactionDefinitionEntity.PENDING, "yak-shaving", "reactions/org-1/yak.png", USER);
        when(repository.findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(ORG,
                ReactionDefinitionEntity.PENDING, USER)).thenReturn(List.of(own));

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

        when(repository.findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(ORG,
                ReactionDefinitionEntity.PENDING, USER)).thenReturn(List.of());

        assertThat(service.mine()).isEmpty();
    }

    @Test
    void approvingAPendingProposalRequiresWritePermission() {

        var pending = anEntity(3L, ReactionDefinitionEntity.PENDING, "clapping", "reactions/org-1/cl.png",
                "other-user");
        when(repository.findByOrganizationIdAndId(ORG, 3L)).thenReturn(Optional.of(pending));

        var approved = service.approve(3L);

        verify(gate).requirePermission(Permission.REACTION_WRITE);
        assertThat(approved.pending()).isFalse();
    }

    @Test
    void approvingAnAlreadyApprovedReactionIsANoOp() {

        var alreadyApproved = anEntity(4L, ReactionDefinitionEntity.APPROVED, "clapping", "reactions/org-1/cl.png",
                USER);
        when(repository.findByOrganizationIdAndId(ORG, 4L)).thenReturn(Optional.of(alreadyApproved));

        var result = service.approve(4L);

        assertThat(result.pending()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void approvingIntoAFullCatalogIsRefused() {

        var pending = anEntity(5L, ReactionDefinitionEntity.PENDING, "clapping", "reactions/org-1/cl.png", USER);
        when(repository.findByOrganizationIdAndId(ORG, 5L)).thenReturn(Optional.of(pending));
        when(repository.countByOrganizationIdAndState(ORG, ReactionDefinitionEntity.APPROVED)).thenReturn(60);

        assertThatThrownBy(() -> service.approve(5L)).isInstanceOf(ConflictException.class).hasMessageContaining("60");
    }

    @Test
    void approvingAnUnknownIdIsNotFound() {

        when(repository.findByOrganizationIdAndId(ORG, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void withdrawingOnesOwnPendingProposalNeedsNoPermission() {

        var own = anEntity(6L, ReactionDefinitionEntity.PENDING, "clapping", "reactions/org-1/cl.png", USER);
        when(repository.findByOrganizationIdAndId(ORG, 6L)).thenReturn(Optional.of(own));

        var key = service.remove(6L);

        assertThat(key).isEqualTo("reactions/org-1/cl.png");
        verify(gate, never()).requirePermission(Permission.REACTION_WRITE);
        verify(repository).delete(own);
    }

    @Test
    void removingSomebodyElsesPendingProposalRequiresWritePermission() {

        var someoneElses = anEntity(7L, ReactionDefinitionEntity.PENDING, "clapping", "reactions/org-1/cl.png",
                "other-user");
        when(repository.findByOrganizationIdAndId(ORG, 7L)).thenReturn(Optional.of(someoneElses));

        service.remove(7L);

        verify(gate).requirePermission(Permission.REACTION_WRITE);
    }

    @Test
    void removingAnApprovedReactionRequiresWritePermission() {

        var approved = anEntity(8L, ReactionDefinitionEntity.APPROVED, "clapping", "reactions/org-1/cl.png", USER);
        when(repository.findByOrganizationIdAndId(ORG, 8L)).thenReturn(Optional.of(approved));

        var key = service.remove(8L);

        assertThat(key).isEqualTo("reactions/org-1/cl.png");
        verify(gate).requirePermission(Permission.REACTION_WRITE);
    }

    @Test
    void removingAnUnknownIdIsNotFound() {

        when(repository.findByOrganizationIdAndId(ORG, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(100L)).isInstanceOf(NotFoundException.class);
    }

    private void givenEntitled() {
        when(paidCapability.isOpenToCaller()).thenReturn(true);
    }

    private static ReactionDefinitionEntity anEntity(Long id, String state, String name, String imageKey,
            String proposedByUserId) {

        return ReactionDefinitionEntity.builder().id(id).organizationId(ORG).name(name).imageKey(imageKey).state(state)
                .proposedByUserId(proposedByUserId).build();
    }
}
