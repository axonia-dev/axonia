package dev.axonia.privacy.application;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.RoleConnectedTestSupport;
import dev.axonia.identity.api.spi.IdentityAccounts;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.onboarding.adapter.out.privacy.OnboardingPersonDataEraser;
import dev.axonia.shared.api.personaldata.PersonDataEraser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code AccountErasure.erasePersonRootedData} fans out over every registered {@link PersonDataEraser}, each carrying
 * its own {@code @Transactional}. Without one transaction around that fan-out, a failure in a later eraser would leave
 * an earlier eraser's writes committed with no rollback and no record of where the fan-out stopped. This class
 * registers a deliberately failing eraser ordered last (see {@link FailingSecondPersonEraser}) rather than relying on
 * which real eraser happens to run after another, so the property is exercised whatever production registers.
 *
 * <p>
 * Runs the REAL {@link OnboardingPersonDataEraser} through the REAL Spring stack against real Postgres - following
 * {@link RoleConnectedTestSupport}, whose container and {@code axonia_app} role wiring this class reuses rather than
 * standing up a third Testcontainers context. A {@code @MockitoSpyBean} on the real eraser (rather than a full mock)
 * proves it actually ran before the failure - not merely that its writes are still there because it was never reached
 * at all, which would pass for the wrong reason.
 */
@CoreSpringBootTest
@Import(AccountErasureAtomicityTest.FailingSecondPersonEraser.class)
class AccountErasureAtomicityTest extends RoleConnectedTestSupport {

    private static final String SUBJECT = "atomicity-target";

    @Autowired
    private AccountErasure accountErasure;

    @MockitoSpyBean
    private OnboardingPersonDataEraser onboardingEraser;

    @MockitoBean
    private OrganizationMembershipSource memberships;

    @MockitoBean
    private IdentityAccounts identityAccounts;

    @AfterEach
    void removeWhatThisClassWrote() throws Exception {
        SecurityContextHolder.clearContext();
        asOwner("DELETE FROM onboarding_path WHERE subject = '" + SUBJECT + "'");
    }

    /**
     * The property this test exists for. {@code onboarding_path} is seeded directly as the container owner - bypassing
     * RLS on the way in is fine, this test only cares what the erasure path itself does - then {@code erase} is
     * expected to run the real {@link OnboardingPersonDataEraser} (which deletes the row), reach the failing second
     * eraser, and have the whole person-rooted fan-out roll back as one unit. Without a transaction around the loop,
     * the real eraser's {@code DELETE} auto-commits on its own connection before the second eraser ever runs, and the
     * row stays gone.
     */
    @Test
    void aFailureInASecondPersonEraserRollsBackTheFirstsWrites() throws Exception {

        when(memberships.membershipsFor(SUBJECT)).thenReturn(List.of());
        asOwner("INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id, "
                + "anchor_community_id, created_at, updated_at) VALUES ('" + SUBJECT + "', 'MEMBER', 'atomicity-path', "
                + "'atomicity-org', 1, now(), now())");

        assertThatThrownBy(() -> accountErasure.erase(SUBJECT)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        // proves the real eraser actually ran - a rolled-back row that was never reached would pass for free
        verify(onboardingEraser).erase(any());
        assertThat(countAsOwner("SELECT count(*) FROM onboarding_path WHERE subject = '" + SUBJECT + "'"))
                .as("the real eraser's DELETE must have rolled back when the second eraser threw").isOne();

        // the identity-provider deletion must never run once the database half is incomplete
        verifyNoInteractions(identityAccounts);
    }

    /**
     * The second, failing {@link PersonDataEraser}. Ordered last ({@link Ordered#LOWEST_PRECEDENCE}) so the real
     * {@link OnboardingPersonDataEraser} - which carries no explicit order - runs before it;
     * {@code verify(onboardingEraser)
     * .erase(any())} above is what actually proves that happened, this annotation is only what makes it reliable rather
     * than accidental.
     */
    @TestConfiguration
    static class FailingSecondPersonEraser {

        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        PersonDataEraser explodingPersonEraser() {
            return erasure -> {
                throw new IllegalStateException("boom");
            };
        }
    }
}
