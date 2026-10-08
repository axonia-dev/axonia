package dev.axonia.organization;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.organization.internal.OrganizationEntity;
import dev.axonia.organization.internal.OrganizationRepository;
import lombok.RequiredArgsConstructor;

/**
 * Hosts the Organization registry: existence, lookup, naming, the communications switch and the lifecycle. Delegated to
 * by {@link dev.axonia.organization.internal.OrganizationsAdapter}.
 *
 * <p>
 * There is deliberately no auto-vivifying "ensure" operation here: an Organization is born complete (registry row, role
 * catalogue, Owner binding, vocabularies) in one provisioning transaction, or not at all. {@link #register} writes only
 * the registry row; its callers (such as {@code OrganizationFounder}) are responsible for assembling the rest of that
 * transaction.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository repository;
    private final OrganizationWriteGate writeGate;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    /**
     * Registers an Organization's registry row. Callers must already be executing under an RLS scope for
     * {@code registration.id()} (see {@code dev.axonia.OrganizationScope}) or the insert violates the organization
     * isolation policy.
     */
    @Transactional
    public Organization register(OrganizationRegistration registration) {
        return toModel(repository.save(OrganizationEntity.builder().id(registration.id()).name(registration.name())
                .kind(registration.kind()).isNew(true).build()));
    }

    @Transactional(readOnly = true)
    public boolean organizationExists(String id) {
        return repository.existsById(id);
    }

    /** Looks up an Organization registry row by id, fully mapped; empty when absent. */
    @Transactional(readOnly = true)
    public Optional<Organization> organization(String id) {
        return repository.findById(id).map(this::toModel);
    }

    /**
     * Renames an Organization. Joins the caller's transaction, so a rename that follows another aggregate's cannot
     * diverge from it. The row must exist: callers resolve the Organization before invoking.
     */
    @Transactional
    public void rename(String id, String name) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Organization vanished from the registry: " + id));
        repository.save(entity.toBuilder().name(name).build());
    }

    /**
     * The organization-wide kill switch for outbound member messaging: on by default, it lets any tenant stop all of it
     * for every member in one place, without asking each of them to unsubscribe individually. Acts on the caller's own
     * Organization; a stale token scoped to a since-deleted Organization deserves a 404, not a silent no-op, so a
     * zero-row update throws rather than returning quietly.
     *
     * <p>
     * Not enterprise-only, and there is deliberately no hub guard on the endpoint: {@code DeliveryGate} checks this
     * switch before it resolves a row's message type at all, so it governs every kind of mail either kind of tenant
     * sends. "All of it" therefore means every message type the edition sends - session notices, wrap-ups, the
     * organizer close-out nudge, the quarterly roundup, and a message about the organization's own sign-in connection
     * where an edition sends one - and the copy an administrator reads, on the switch and in its confirm dialog, names
     * each one. A new message type is a new item there: a type missing from that list is one they hear about from a
     * recipient instead.
     *
     * <p>
     * Gated on {@code communication:write} rather than on the generic {@code organization:write} it used to ride on:
     * see {@link OrganizationWriteGate#requireCommunicationWrite()}.
     */
    @Transactional
    public Organization setCommunicationsEnabled(boolean enabled) {
        writeGate.requireCommunicationWrite();
        var id = organizationContext.organizationId();
        if (repository.updateCommunicationsEnabled(id, enabled, clock.instant()) == 0) {
            throw new NotFoundException("Organization not found: " + id);
        }
        return organization(id).orElseThrow(() -> new NotFoundException("Organization not found: " + id));
    }

    /**
     * Removes an Organization's registry row - the final local step of deleting it. The caller has already purged every
     * other organization-scoped row in the same transaction. Refuses an Organization of another kind than the caller
     * deletes, so a deletion meant for one kind can never take a tenant of the other with it.
     */
    @Transactional
    public void delete(String id, OrganizationKind kind) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Organization not found: " + id));
        if (entity.getKind() != kind) {
            throw new IllegalStateException("Organization is not of kind " + kind + ": " + id);
        }
        repository.deleteById(id);
    }

    /** Moves an Organization from {@code ACTIVE} to {@code SUSPENDED}. */
    @Transactional
    public void suspend(String id) {
        transition(id, OrganizationLifecycleState.ACTIVE, OrganizationLifecycleState.SUSPENDED);
    }

    /** Moves an Organization from {@code SUSPENDED} back to {@code ACTIVE}. */
    @Transactional
    public void unsuspend(String id) {
        transition(id, OrganizationLifecycleState.SUSPENDED, OrganizationLifecycleState.ACTIVE);
    }

    /**
     * Confirms an Organization is eligible for closing - {@code SUSPENDED} or already {@code CLOSED} - and returns it.
     * {@code ACTIVE} organizations must be suspended first.
     */
    @Transactional
    public Organization requireClosable(String id) {
        var entity = existingEntity(id);
        if (entity.getLifecycleState() == OrganizationLifecycleState.ACTIVE) {
            throw new ConflictException("invalid_lifecycle_transition",
                    "Organization must be suspended before it can be closed: " + id);
        }
        return toModel(entity);
    }

    /** Stamps an Organization as {@code CLOSED}, recording when and by whom. */
    @Transactional
    public Organization markClosed(String id, String operatorSubject) {
        var entity = existingEntity(id);
        var closed = entity.toBuilder().lifecycleState(OrganizationLifecycleState.CLOSED).closedAt(clock.instant())
                .closedBy(operatorSubject).build();
        return toModel(repository.save(closed));
    }

    private void transition(String id, OrganizationLifecycleState from, OrganizationLifecycleState to) {
        var entity = existingEntity(id);
        if (entity.getLifecycleState() != from) {
            throw new ConflictException("invalid_lifecycle_transition",
                    "Cannot move " + id + " from " + entity.getLifecycleState() + " to " + to);
        }
        repository.save(entity.toBuilder().lifecycleState(to).build());
    }

    private OrganizationEntity existingEntity(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Organization not found: " + id));
    }

    private Organization toModel(OrganizationEntity entity) {
        return Organization.builder().id(entity.getId()).name(entity.getName()).kind(entity.getKind())
                .lifecycleState(entity.getLifecycleState()).communicationsEnabled(entity.isCommunicationsEnabled())
                .build();
    }
}
