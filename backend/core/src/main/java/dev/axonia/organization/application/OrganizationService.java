package dev.axonia.organization.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.organization.api.spi.OrganizationWriteGate;
import dev.axonia.organization.application.port.out.OrganizationContext;
import dev.axonia.organization.application.port.out.OrganizationStore;
import dev.axonia.organization.domain.RegistryEntry;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Hosts the Organization registry: existence, lookup, naming, the communications switch and the lifecycle. Delegated to
 * by {@link dev.axonia.organization.application.OrganizationsAdapter}.
 *
 * <p>
 * There is deliberately no auto-vivifying "ensure" operation here: an Organization is born complete (registry row, role
 * catalogue, Owner binding, vocabularies) in one provisioning transaction, or not at all. {@link #register} writes only
 * the registry row; its callers (such as {@code OrganizationFounder}) are responsible for assembling the rest of that
 * transaction.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService implements OrganizationRegistry {

    private final OrganizationStore store;
    private final OrganizationWriteGate writeGate;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    /**
     * Registers an Organization's registry row. Callers must already be executing under an RLS scope for
     * {@code registration.id()} (see {@code dev.axonia.shared.api.tenancy.OrganizationScope}) or the insert violates
     * the organization isolation policy.
     */
    @Override
    @Transactional
    public Organization register(OrganizationRegistration registration) {
        return toModel(store.register(registration));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean organizationExists(String id) {
        return store.exists(id);
    }

    /** Looks up an Organization registry row by id, fully mapped; empty when absent. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Organization> organization(String id) {
        return store.find(id).map(this::toModel);
    }

    /**
     * Renames an Organization. Joins the caller's transaction, so a rename that follows another aggregate's cannot
     * diverge from it. The row must exist: callers resolve the Organization before invoking.
     */
    @Override
    @Transactional
    public void rename(String id, String name) {
        var entry = store.find(id)
                .orElseThrow(() -> new IllegalStateException("Organization vanished from the registry: " + id));
        store.update(entry.toBuilder().name(name).build());
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
        if (store.setCommunicationsEnabled(id, enabled, clock.instant()) == 0) {
            throw new NotFoundException("Organization not found: " + id);
        }
        return organization(id).orElseThrow(() -> new NotFoundException("Organization not found: " + id));
    }

    /**
     * Removes an Organization's registry row - the final local step of deleting it. The caller has already purged every
     * other organization-scoped row in the same transaction. Refuses an Organization of another kind than the caller
     * deletes, so a deletion meant for one kind can never take a tenant of the other with it.
     */
    @Override
    @Transactional
    public void delete(String id, OrganizationKind kind) {
        var entry = store.find(id).orElseThrow(() -> new IllegalStateException("Organization not found: " + id));
        if (entry.kind() != kind) {
            throw new IllegalStateException("Organization is not of kind " + kind + ": " + id);
        }
        store.delete(id);
    }

    /** Moves an Organization from {@code ACTIVE} to {@code SUSPENDED}. */
    @Override
    @Transactional
    public void suspend(String id) {
        transition(id, OrganizationLifecycleState.ACTIVE, OrganizationLifecycleState.SUSPENDED);
    }

    /** Moves an Organization from {@code SUSPENDED} back to {@code ACTIVE}. */
    @Override
    @Transactional
    public void unsuspend(String id) {
        transition(id, OrganizationLifecycleState.SUSPENDED, OrganizationLifecycleState.ACTIVE);
    }

    /**
     * Confirms an Organization is eligible for closing - {@code SUSPENDED} or already {@code CLOSED} - and returns it.
     * {@code ACTIVE} organizations must be suspended first.
     */
    @Override
    @Transactional
    public Organization requireClosable(String id) {
        var entry = existingEntry(id);
        if (entry.lifecycleState() == OrganizationLifecycleState.ACTIVE) {
            throw new ConflictException("invalid_lifecycle_transition",
                    "Organization must be suspended before it can be closed: " + id);
        }
        return toModel(entry);
    }

    /** Stamps an Organization as {@code CLOSED}, recording when and by whom. */
    @Override
    @Transactional
    public Organization markClosed(String id, String operatorSubject) {
        var entry = existingEntry(id);
        var closed = entry.toBuilder().lifecycleState(OrganizationLifecycleState.CLOSED).closedAt(clock.instant())
                .closedBy(operatorSubject).build();
        return toModel(store.update(closed));
    }

    private void transition(String id, OrganizationLifecycleState from, OrganizationLifecycleState to) {
        var entry = existingEntry(id);
        if (entry.lifecycleState() != from) {
            throw new ConflictException("invalid_lifecycle_transition",
                    "Cannot move " + id + " from " + entry.lifecycleState() + " to " + to);
        }
        store.update(entry.toBuilder().lifecycleState(to).build());
    }

    private RegistryEntry existingEntry(String id) {
        return store.find(id).orElseThrow(() -> new NotFoundException("Organization not found: " + id));
    }

    private Organization toModel(RegistryEntry entry) {
        return Organization.builder().id(entry.id()).name(entry.name()).kind(entry.kind())
                .lifecycleState(entry.lifecycleState()).communicationsEnabled(entry.communicationsEnabled()).build();
    }
}
