package dev.axonia.selfhosted.application;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;

import dev.axonia.selfhosted.FreshInstallation;
import dev.axonia.selfhosted.SelfHostedTestInfrastructure;
import dev.axonia.selfhosted.adapter.in.event.SelfHostedOrganization;
import dev.axonia.selfhosted.domain.Installation;

import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_ID;
import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_NAME;
import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.OWNER_GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The founding at boot on an installation of its own, which the application migrates and reaches as its runtime role,
 * so the Organization scope, the grants and the founding lock are production's. The shared Self-hosted test database is
 * never touched. Starts are expensive, so one application founds the installation and the later steps call its founding
 * directly; real starts prove the start itself: one that changes nothing, one that is refused, and two that found a
 * fresh installation together.
 */
class SelfHostedFoundingIntegrationTest {

    private static final String OWNER_BINDINGS = "SELECT b.subject_type, b.subject_id, b.subject_label FROM role_binding b"
            + " JOIN role r ON r.id = b.role_id WHERE r.name = 'Owner' ORDER BY b.id";

    private static FreshInstallation installation;

    private static ConfigurableApplicationContext application;

    @BeforeAll
    static void firstStart() throws SQLException {

        installation = FreshInstallation.create();
        application = SelfHostedTestInfrastructure.start(installation.settings(Map.of()));
    }

    @AfterAll
    static void stop() {

        application.close();
    }

    @Test
    void foundsTheOrganizationOnFirstStart() throws SQLException {

        assertThat(installation.rows("SELECT id, name, kind, lifecycle_state FROM organization"))
                .containsExactly(ORGANIZATION_ID + "|" + ORGANIZATION_NAME + "|ENTERPRISE|ACTIVE");
        assertThat(installation.rows("SELECT organization_id, subject_claim FROM selfhosted_settings"))
                .containsExactly(ORGANIZATION_ID + "|sub");
        assertThat(installation.rows(OWNER_BINDINGS)).contains("GROUP|" + OWNER_GROUP + "|Owners");
    }

    @Test
    void aSecondStartChangesNothing() throws SQLException {

        // given
        var before = installation.snapshot();

        // when
        try (var second = SelfHostedTestInfrastructure.start(installation.settings(Map.of()))) {

            // then
            assertThat(installation.snapshot()).isEqualTo(before);
        }
    }

    @Test
    void refusesToStartWhenTheSubjectClaimChanged() throws SQLException {

        // given
        var before = installation.snapshot();

        // when / then
        assertThatThrownBy(() -> SelfHostedTestInfrastructure
                .start(installation.settings(Map.of("axonia.selfhosted.oidc.subject-claim", "oid"))))
                .satisfies(refusal -> assertThat(NestedExceptionUtils.getMostSpecificCause(refusal))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("'sub'")
                        .hasMessageContaining("'oid'"));
        assertThat(installation.snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAnOrganizationIdItWasNotFoundedWith() throws SQLException {

        // given
        var before = installation.snapshot();

        // when / then
        assertThatThrownBy(() -> SelfHostedTestInfrastructure
                .start(installation.settings(Map.of("axonia.selfhosted.organization.id", "globex"))))
                .satisfies(refusal -> assertThat(NestedExceptionUtils.getMostSpecificCause(refusal))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("'" + ORGANIZATION_ID + "'")
                        .hasMessageContaining("'globex'"));
        assertThat(installation.snapshot()).isEqualTo(before);
    }

    @Test
    void addsTheNewOwnerGroupAndKeepsTheOld() throws SQLException {

        // when
        establish(new Installation(ORGANIZATION_ID, ORGANIZATION_NAME, "platform-admins", "sub"));
        establish(new Installation(ORGANIZATION_ID, ORGANIZATION_NAME, "platform-admins", "sub"));

        // then
        assertThat(installation.rows(OWNER_BINDINGS)).contains("GROUP|" + OWNER_GROUP + "|Owners")
                .containsOnlyOnce("GROUP|platform-admins|Owners");
    }

    @Test
    void aChangedNameRenamesTheOrganization() throws SQLException {

        try {
            // when
            establish(new Installation(ORGANIZATION_ID, "Acme Corporation", OWNER_GROUP, "sub"));

            // then
            assertThat(installation.rows("SELECT name FROM organization")).containsExactly("Acme Corporation");
        } finally {
            establish(new Installation(ORGANIZATION_ID, ORGANIZATION_NAME, OWNER_GROUP, "sub"));
        }
    }

    @Test
    void startsRacingEachOtherBindANewOwnerGroupOnce() throws Exception {

        for (var round = 0; round < 10; round++) {
            // given
            var group = "racing-owners-" + round;
            var barrier = new CyclicBarrier(2);

            // when
            try (var executor = Executors.newFixedThreadPool(2)) {
                var racers = List.of(executor.submit(race(barrier, group)), executor.submit(race(barrier, group)));
                for (var racer : racers) {
                    racer.get(30, TimeUnit.SECONDS);
                }
            }

            // then
            assertThat(installation.rows(OWNER_BINDINGS)).containsOnlyOnce("GROUP|" + group + "|Owners");
        }
    }

    /**
     * Two replicas of a new installation: the first waits at {@link ReadyTogether} until the second is ready too, so
     * both found at the same moment, and only the founding lock keeps the second from founding again.
     */
    @Test
    void twoFirstStartsOnAFreshInstallationFoundOnce() throws Exception {

        // given
        var fresh = FreshInstallation.create();
        var settings = fresh.settings(Map.of("spring.main.sources", ReadyTogether.class.getName()));

        try (var executor = Executors.newFixedThreadPool(2)) {
            // when
            var first = executor.submit(() -> SelfHostedTestInfrastructure.start(settings));
            await().atMost(3, TimeUnit.MINUTES)
                    .until(() -> first.isDone() || ReadyTogether.BARRIER.getNumberWaiting() == 1);
            var second = executor.submit(() -> SelfHostedTestInfrastructure.start(settings));
            try (var one = first.get(3, TimeUnit.MINUTES); var two = second.get(3, TimeUnit.MINUTES)) {

                // then
                assertThat(one.isActive()).isTrue();
                assertThat(two.isActive()).isTrue();
                assertThat(fresh.rows("SELECT id FROM organization")).containsExactly(ORGANIZATION_ID);
                assertThat(fresh.rows("SELECT organization_id FROM selfhosted_settings"))
                        .containsExactly(ORGANIZATION_ID);
                assertThat(fresh.rows(OWNER_BINDINGS)).containsExactly("GROUP|" + OWNER_GROUP + "|Owners");
            }
        }
    }

    private static Callable<Void> race(CyclicBarrier barrier, String group) {

        return () -> {
            barrier.await();
            establish(new Installation(ORGANIZATION_ID, ORGANIZATION_NAME, group, "sub"));
            return null;
        };
    }

    private static void establish(Installation configured) {

        application.getBean(SelfHostedOrganization.class).establish(configured);
    }

    /**
     * Holds each application at ready, ahead of the founding listener, until the other one is ready too. Added to an
     * application through {@code spring.main.sources}, so no other test's context has it.
     */
    static final class ReadyTogether implements ApplicationListener<ApplicationReadyEvent>, Ordered {

        static final CyclicBarrier BARRIER = new CyclicBarrier(2);

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {

            try {
                BARRIER.await(3, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the other start", e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("the other start never became ready", e);
            }
        }

        @Override
        public int getOrder() {

            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
