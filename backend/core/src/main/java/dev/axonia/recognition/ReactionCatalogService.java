package dev.axonia.recognition;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import lombok.RequiredArgsConstructor;

/**
 * The Organization's own reaction vocabulary.
 *
 * <p>
 * Authoring is free and belongs to whoever holds {@code reaction:write}. <em>Proposing</em> is on the paid axis - see
 * {@link PaidCapability}. A proposal is invisible and unusable, including to the person who made it, until somebody
 * accountable approves it. That is the whole moderation model: nothing member-uploaded is ever visible to another
 * member without an approval behind it.
 */
@Service
@RequiredArgsConstructor
public class ReactionCatalogService {

    /** Bounds the picker's first group, which is the one nobody can scroll past. */
    static final int MAX_APPROVED = 60;

    /** Stops one member filling a queue somebody else has to clear. */
    static final int MAX_OPEN_PROPOSALS = 3;

    private static final int NAME_MIN = 2;

    private static final int NAME_MAX = 32;

    private final ReactionDefinitionRepository repository;

    private final OrganizationContext organizationContext;

    private final CurrentUser currentUser;

    private final AuthorizationGate gate;

    private final PaidCapability paidCapability;

    private final MediaLinks mediaLinks;

    /** What every member may use. Ungated: without it there is nothing to react with. */
    @Transactional(readOnly = true)
    public List<ReactionDefinition> approved() {

        return repository.findByOrganizationIdAndStateOrderByNameAsc(organizationContext.organizationId(),
                ReactionDefinitionEntity.APPROVED).stream().map(e -> toDefinition(e, 0L)).toList();
    }

    /**
     * The administration view: the queue as well, with who proposed each, and how many placements ride on each approved
     * reaction. Gated on {@code reaction:read}. The usage count is a join query {@link #approved()} never pays for - no
     * member's page draws it.
     */
    @Transactional(readOnly = true)
    public List<ReactionDefinition> all() {

        gate.requirePermission(Permission.REACTION_READ);
        var organizationId = organizationContext.organizationId();
        var usageByDefinition = repository.countUsageByDefinition(organizationId).stream()
                .collect(Collectors.toMap(ReactionDefinitionRepository.DefinitionUsage::definitionId,
                        ReactionDefinitionRepository.DefinitionUsage::usageCount));
        return repository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(e -> toDefinition(e, usageByDefinition.getOrDefault(e.getId(), 0L))).toList();
    }

    /**
     * The caller's own proposals, still waiting. Without it the open-proposal cap is a refusal naming nothing: a member
     * who has filled it is told at most three may be open and has no way to see which three, let alone take one back.
     * <p>
     * Ungated on purpose. {@code reaction:read} governs the queue as an administration surface - everybody's rows, with
     * the proposers' names - and this is narrower than that by construction: it answers with nothing the caller did not
     * write themselves. Pending rows only, because an approved reaction belongs to the Organization from the moment it
     * is approved and is no longer its proposer's to withdraw.
     */
    @Transactional(readOnly = true)
    public List<ReactionDefinition> mine() {

        return repository
                .findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(organizationContext.organizationId(),
                        ReactionDefinitionEntity.PENDING, currentUser.userId())
                .stream().map(e -> toDefinition(e, 0L)).toList();
    }

    @Transactional(readOnly = true)
    public ReactionEligibility eligibility() {

        return ReactionEligibility.builder().mayPropose(mayPropose()).openProposals(openProposalsOfCaller())
                .openProposalLimit(MAX_OPEN_PROPOSALS).approvedLimit(MAX_APPROVED).build();
    }

    /**
     * The same refusal {@link #propose(String, String)} makes, available before an image has been read or stored. It
     * exists so the web edge can settle authorization first and spend nothing on a caller it is about to turn away;
     * {@code propose} still makes the check itself, so this is an earlier answer to the same question, never the only
     * one.
     */
    public void requireMayPropose() {

        requireEligibleToPropose(gate.hasPermission(Permission.REACTION_WRITE));
    }

    @Transactional
    public ReactionDefinition propose(String name, String imageKey) {

        var publishesDirectly = gate.hasPermission(Permission.REACTION_WRITE);
        requireEligibleToPropose(publishesDirectly);
        var organizationId = organizationContext.organizationId();
        var trimmed = requireValidName(name);
        requireRoomForOneMore(organizationId, trimmed, publishesDirectly);
        var userId = currentUser.userId();
        var saved = repository
                .save(ReactionDefinitionEntity.builder().organizationId(organizationId).name(trimmed).imageKey(imageKey)
                        .state(publishesDirectly ? ReactionDefinitionEntity.APPROVED : ReactionDefinitionEntity.PENDING)
                        .proposedByUserId(userId).approvedByUserId(publishesDirectly ? userId : null).build());
        return toDefinition(saved, 0L);
    }

    @Transactional
    public ReactionDefinition approve(Long id) {

        gate.requirePermission(Permission.REACTION_WRITE);
        var organizationId = organizationContext.organizationId();
        var entity = require(organizationId, id);
        if (ReactionDefinitionEntity.APPROVED.equals(entity.getState())) {
            return toDefinition(entity, 0L);
        }
        if (repository.countByOrganizationIdAndState(organizationId,
                ReactionDefinitionEntity.APPROVED) >= MAX_APPROVED) {
            throw new ConflictException("REACTION_LIMIT_REACHED",
                    "An organization holds at most " + MAX_APPROVED + " custom reactions");
        }
        return toDefinition(repository.save(entity.toBuilder().state(ReactionDefinitionEntity.APPROVED)
                .approvedByUserId(currentUser.userId()).build()), 0L);
    }

    /**
     * Declining a proposal, withdrawing one's own, and removing an approved reaction are one act with three names.
     * Returns the image key whose blob the caller deletes <em>after</em> this transaction has committed - object
     * storage has no rollback, so a delete issued before commit outlives a commit that then fails.
     */
    @Transactional
    public String remove(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = require(organizationId, id);
        var isOwnPendingProposal = ReactionDefinitionEntity.PENDING.equals(entity.getState())
                && currentUser.userId().equals(entity.getProposedByUserId());
        if (!isOwnPendingProposal) {
            gate.requirePermission(Permission.REACTION_WRITE);
        }
        repository.delete(entity);
        return entity.getImageKey();
    }

    /**
     * The picker's footer must not offer less than {@link #propose(String, String)} accepts: a {@code reaction:write}
     * holder's own upload is created APPROVED rather than queued, so holding write is already the accountable act,
     * exactly as the paid axis is.
     */
    private boolean mayPropose() {

        return gate.hasPermission(Permission.REACTION_WRITE) || paidCapability.isOpenToCaller();
    }

    private void requireEligibleToPropose(boolean publishesDirectly) {

        if (!publishesDirectly && !mayPropose()) {
            throw new ForbiddenException(paidCapability.refusal(PaidAction.PROPOSE_REACTION));
        }
    }

    private void requireRoomForOneMore(String organizationId, String trimmed, boolean publishesDirectly) {

        if (repository.existsByOrganizationIdAndName(organizationId, trimmed)) {
            throw new ConflictException("REACTION_EXISTS",
                    "This organization already has a reaction called " + trimmed);
        }
        if (repository.countByOrganizationIdAndState(organizationId,
                ReactionDefinitionEntity.APPROVED) >= MAX_APPROVED) {
            throw new ConflictException("REACTION_LIMIT_REACHED",
                    "An organization holds at most " + MAX_APPROVED + " custom reactions");
        }
        if (!publishesDirectly && openProposalsOfCaller() >= MAX_OPEN_PROPOSALS) {
            throw new ConflictException("PROPOSAL_LIMIT_REACHED",
                    "At most " + MAX_OPEN_PROPOSALS + " proposals can be open at once");
        }
    }

    private int openProposalsOfCaller() {

        return repository.countByOrganizationIdAndStateAndProposedByUserId(organizationContext.organizationId(),
                ReactionDefinitionEntity.PENDING, currentUser.userId());
    }

    private ReactionDefinitionEntity require(String organizationId, Long id) {

        return repository.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Reaction not found: " + id));
    }

    private static String requireValidName(String name) {

        var trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < NAME_MIN || trimmed.length() > NAME_MAX) {
            throw new ValidationException("INVALID_REACTION_NAME",
                    "A reaction's name is between " + NAME_MIN + " and " + NAME_MAX + " characters");
        }
        return trimmed;
    }

    private ReactionDefinition toDefinition(ReactionDefinitionEntity e, long usageCount) {

        var pending = ReactionDefinitionEntity.PENDING.equals(e.getState());
        return ReactionDefinition.builder().id(e.getId()).name(e.getName()).imageUrl(mediaLinks.urlFor(e.getImageKey()))
                .pending(pending).proposedByUserId(pending ? e.getProposedByUserId() : null).usageCount(usageCount)
                .build();
    }
}
