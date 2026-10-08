package dev.axonia.communication.internal;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test standing between concurrent draining and a silent, total failure. A worker thread that does not enter
 * the organization scope itself opens its transaction with no {@code app.current_org}, and Row-Level Security answers
 * an unscoped query with zero rows rather than an error - so the row would read as "nothing to do" instead of failing.
 * Nothing about that is visible without this test: {@code OrganizationScope} stores the acting organization in
 * {@link SecurityContextHolder}, whose default strategy is thread-local and NOT inheritable, so the scope the drain
 * thread is running in does not reach a worker on its own.
 *
 * <p>
 * The assertion reads {@link OrganizationBoundAuthentication#organizationId()}, not {@code getPrincipal()}, because
 * that is the value {@code PersistenceConfig.organizationResolver()} actually reads when the transaction acquires its
 * connection - {@code ProvisioningAuthentication#getPrincipal()} returns the constant {@code "provisioning"} for every
 * organization alike and would stay green with the wrong tenant's scope, or with a scope naming no tenant at all.
 */
class DrainWorkerScopeTest {

    private static final String ORGANIZATION_ID = "acme";
    private static final String CALLING_ORGANIZATION_ID = "the-sweeps-own-organization";
    private static final String UNSCOPED = "no organization scope on this thread";

    private static final OutgoingMail MAIL = new OutgoingMail("member@example.com", "Subject", "<p>html</p>", "text",
            null);

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void everyWorkerThreadSeesTheOrganizationScopeItWasGiven() {

        var seen = new CopyOnWriteArrayList<String>();
        var rows = List.of(rowWithId(1L), rowWithId(2L), rowWithId(3L), rowWithId(4L));
        var workers = poolOf((row, batch) -> {
            seen.add(scopedOrganizationId());
            return Optional.empty();
        }, 4);

        workers.runAll(ORGANIZATION_ID, rows, DrainWorkerScopeTest::noOpBatch, draining());

        assertThat(seen).hasSize(4).allSatisfy(organizationId -> assertThat(organizationId).isEqualTo(ORGANIZATION_ID));
    }

    /**
     * The scope belongs to the worker, not to the drain thread that dispatched it, and it must not travel the other way
     * either. In production the calling thread is NOT unscoped: {@code CommunicationSweep} runs the whole
     * per-organization unit inside its own {@code OrganizationScope}, and {@code markFailed} and {@code release} are
     * RLS-scoped writes issued on that thread once the workers come back. A worker that left its own scope behind, or
     * cleared what it found, would silently redirect those writes at the wrong tenant or at no tenant at all - which
     * RLS answers with zero rows rather than an error. The caller therefore starts inside a DIFFERENT organization
     * here, and must still be in it afterwards.
     */
    @Test
    void theCallingThreadsOwnScopeIsUntouched() {

        var workers = poolOf((row, batch) -> Optional.empty(), 2);

        OrganizationScope.runAs(CALLING_ORGANIZATION_ID, () -> {
            workers.runAll(ORGANIZATION_ID, List.of(rowWithId(1L)), DrainWorkerScopeTest::noOpBatch, draining());
            assertThat(scopedOrganizationId()).isEqualTo(CALLING_ORGANIZATION_ID);
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * The mail connection is opened inside the scope too, not only the row work. It is opened lazily, by the first
     * message that has a send slot, and a supplier that reached for anything organization-scoped - a per-tenant
     * credential, say - would otherwise be the one call in a worker's life running unscoped.
     */
    @Test
    void theMailConnectionIsOpenedInsideTheWorkersOwnScope() {

        var seen = new CopyOnWriteArrayList<String>();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 1);

        workers.runAll(ORGANIZATION_ID, List.of(rowWithId(1L)), () -> {
            seen.add(scopedOrganizationId());
            return noOpBatch();
        }, draining());

        assertThat(seen).containsExactly(ORGANIZATION_ID);
    }

    /** A tick that is draining normally, with the vendor's pace never in the way. */
    private static DrainWorkers.SendWindow draining() {

        return new DrainWorkers.SendWindow(() -> true, () -> true, new MailAcceptance());
    }

    private static String scopedOrganizationId() {

        return SecurityContextHolder.getContext().getAuthentication() instanceof OrganizationBoundAuthentication bound
                ? bound.organizationId()
                : UNSCOPED;
    }

    private static OutboxEntity rowWithId(long id) {

        return OutboxEntity.builder().id(id).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .template("session-wrap-up").status("SENDING").build();
    }

    /**
     * {@code DrainWorkers}' four-argument constructor, with a fake chat destinations port and a real clock: nothing in
     * this class sends a chat row, so neither is ever read, but the class genuinely has no fewer-argument constructor
     * for a test to fall back onto.
     */
    private static DrainWorkers poolOf(DrainWorkers.RowWork work, int workerCount) {

        return new DrainWorkers(work, workerCount, new StubChatDestinationsPort(), Clock.systemUTC());
    }

    private static MailBatch noOpBatch() {

        return new MailBatch() {

            @Override
            public void send(OutgoingMail mail) {
            }

            @Override
            public void close() {
            }
        };
    }
}
