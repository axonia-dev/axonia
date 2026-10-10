package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.adapter.out.persistence.JdbcUserStore;
import dev.axonia.identity.adapter.out.persistence.UserEntity;
import dev.axonia.identity.adapter.out.persistence.UserRepository;
import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.identity.api.spi.SignInProviderResolver;
import dev.axonia.identity.domain.UserArrival;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.shared.api.metrics.UsageCounters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link UserService#provision} against real Postgres, because the thing that needs proving here is one a mock cannot
 * reach: whether the statements this path issues can violate {@code uq_identity_user_organization_user}. That is why
 * this lives here rather than beside its siblings in {@code UserServiceTest} and {@code UserServiceReconcileTest},
 * which mock the repository - a mocked {@code save} stores nothing and a mocked {@code attachSub} accepts anything, so
 * in those the constraint being violated does not exist at all and the reproduction would have gone green.
 *
 * <p>
 * The service is assembled by hand rather than through {@code @Import}, and none of its collaborators is a
 * {@code @MockitoBean}; its store is the real bean, under the exact {@code @Import} set
 * {@code UserDirectoryAdapterTest} already uses, so this class adds no new Spring configuration and joins that cached
 * context. A distinct {@code @Import} set would have cached another context with its own Hikari pool against the one
 * shared Testcontainers Postgres, which is how the full gate has run out of connections before.
 */
@Import({UserDirectoryAdapter.class, JdbcUserStore.class})
class UserServiceProvisionJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "provision-org";

    private static final String SUBJECT = "sub-ada";

    private static final Instant SEEN_AT = Instant.parse("2026-09-11T09:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcUserStore userStore;

    private UserService userService;

    @BeforeEach
    void assembleService() {

        userService = new UserService(userStore, mock(ClaimsReader.class), mock(AvatarSyncService.class),
                mock(SignInProviderResolver.class), new UsageCounters(new SimpleMeterRegistry()),
                new MediaLinks("https://media.example.test"), Clock.fixed(SEEN_AT, ZoneOffset.UTC));
    }

    /**
     * The defect: a member already has a row under their subject from an earlier sign-in, and a directory has since
     * pushed a second, sub-less row for them at a new address. Signing in with the new address made the reconciliation
     * arm attach the subject to the pushed row, which is an unguarded {@code UPDATE identity_user SET user_id} onto a
     * subject the first row still holds - {@code uq_identity_user_organization_user}, and a 500 on that member's very
     * next request.
     */
    @Test
    void aSublessRowIsNotReconciledOntoASubjectAnotherRowAlreadyHolds() {

        userRepository.save(UserEntity.builder().organizationId(ORG).userId(SUBJECT).scimId("scim-ada")
                .arrival(UserArrival.FIRST_SIGN_IN).displayName("Ada").email("ada@old.test").active(true).build());
        var pushed = userRepository.save(UserEntity.builder().organizationId(ORG).scimId("scim-pushed")
                .arrival(UserArrival.SYNCED).displayName("Ada").email("ada@new.test").active(true).build());

        userService.provision(IdentityClaims.builder().organizationId(ORG).userId(SUBJECT).displayName("Ada")
                .email("ada@new.test").build());

        assertThat(userRepository.findByOrganizationIdAndUserId(ORG, SUBJECT)).get().extracting(UserEntity::getScimId)
                .as("the subject stayed on the row that already held it").isEqualTo("scim-ada");
        assertThat(userRepository.findById(pushed.getId())).get().extracting(UserEntity::getUserId)
                .as("the pushed row was left sub-less rather than dragged onto an occupied subject").isNull();
    }

    /** The other side of the guard: with no row under this subject yet, a pushed row is still reconciled. */
    @Test
    void aSublessRowIsStillReconciledWhenTheMemberHasNoRowUnderThisSubject() {

        var pushed = userRepository.save(UserEntity.builder().organizationId(ORG).scimId("scim-grace")
                .arrival(UserArrival.SYNCED).displayName("Grace").email("grace@acme.test").active(true).build());

        userService.provision(IdentityClaims.builder().organizationId(ORG).userId("sub-grace").displayName("Grace")
                .email("grace@acme.test").build());

        assertThat(userRepository.findById(pushed.getId())).get().extracting(UserEntity::getUserId)
                .isEqualTo("sub-grace");
    }
}
