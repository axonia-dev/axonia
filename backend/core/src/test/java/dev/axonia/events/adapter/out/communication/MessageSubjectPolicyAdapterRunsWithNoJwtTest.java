package dev.axonia.events.adapter.out.communication;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.OrganizationOwners;
import dev.axonia.communication.api.MessageType;
import dev.axonia.community.adapter.out.persistence.CommunityEntityMother;
import dev.axonia.community.adapter.out.persistence.CommunityKindRepository;
import dev.axonia.community.adapter.out.persistence.CommunityRepository;
import dev.axonia.community.adapter.out.persistence.JdbcCommunityKindStore;
import dev.axonia.community.adapter.out.persistence.JdbcCommunityStore;
import dev.axonia.community.adapter.out.persistence.JdbcMembershipLedger;
import dev.axonia.community.adapter.out.persistence.MembershipEntityMother;
import dev.axonia.community.adapter.out.persistence.MembershipRepository;
import dev.axonia.community.api.ChatDestinations;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.CommunityCapabilities;
import dev.axonia.community.api.MemberDirectory;
import dev.axonia.community.api.Memberships;
import dev.axonia.community.application.CommunityCountRule;
import dev.axonia.community.application.CommunityLookup;
import dev.axonia.community.application.CommunityPolicyService;
import dev.axonia.community.application.CommunityService;
import dev.axonia.community.application.MembershipAuthorizer;
import dev.axonia.community.application.MembershipService;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.events.adapter.out.community.CommunityAccessAdapter;
import dev.axonia.events.adapter.out.persistence.SessionEntityMother;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.shared.api.metrics.UsageCounters;
import dev.axonia.shared.api.security.CurrentToken;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces, without Spring or Testcontainers, the exact failure the drain hit in production: the delivery gate runs
 * inside {@code OrganizationScope.runAs}, which installs a {@code ProvisioningAuthentication} - not a
 * {@code JwtAuthenticationToken}. {@code community}'s {@code OrganizationContext} resolves the organization from a JWT
 * ({@link CurrentToken#organization()}), which throws {@link IllegalStateException} whenever the
 * {@code SecurityContext}'s authentication is not a JWT.
 *
 * <p>
 * This wires the REAL {@link Communities} and {@link Memberships} - with the REAL, throwing
 * {@code communityJwtOrganizationContext} behaviour ({@code CurrentToken::organization}, not a mock) - through the REAL
 * {@link CommunityAccessAdapter} into {@link MessageSubjectPolicyAdapter}. A regression that routes either adapter
 * method back through an implicit-organization {@code CommunityAccess} overload fails this test with the same exception
 * production throws, rather than passing vacuously against a mock that never touches {@code OrganizationContext} at
 * all.
 */
class MessageSubjectPolicyAdapterRunsWithNoJwtTest {

    private static final String ORGANIZATION_ID = "acme";

    private final CommunityRepository communityRepository = mock(CommunityRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);

    private final MessageSubjectPolicyAdapter sut = buildSut();

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void isEnabledForRunsInsideTheSweepWithNoJwtInTheSecurityContext() {

        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 7L))
                .thenReturn(Optional.of(CommunityEntityMother.any().id(7L).notifySessionWrapUp(true).build()));

        var result = OrganizationScope.runAs(ORGANIZATION_ID,
                () -> sut.isEnabledFor(ORGANIZATION_ID, MessageType.SESSION_WRAP_UP, "42"));

        assertThat(result).isTrue();
    }

    @Test
    void isStillAMemberRunsInsideTheSweepWithNoJwtInTheSecurityContext() {

        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(ORGANIZATION_ID, 7L, "alice"))
                .thenReturn(Optional.of(MembershipEntityMother.any().build()));

        var result = OrganizationScope.runAs(ORGANIZATION_ID, () -> sut.isStillAMember(ORGANIZATION_ID, "42", "alice"));

        assertThat(result).isTrue();
    }

    /**
     * Proves the fixture itself carries no JWT during the sweep, so the two tests above are not passing by accident:
     * the implicit-organization path genuinely throws in this exact {@code SecurityContext} state.
     */
    @Test
    void theFixtureCarriesNoJwtSoTheImplicitOrganizationContextThrows() {

        OrganizationScope.runAs(ORGANIZATION_ID,
                () -> assertThatThrownBy(CurrentToken::organization).isInstanceOf(IllegalStateException.class));
    }

    private MessageSubjectPolicyAdapter buildSut() {

        var organizationContext = communityOrganizationContext();
        var communityStore = new JdbcCommunityStore(communityRepository, mock(JdbcAggregateTemplate.class));
        var membershipLedger = new JdbcMembershipLedger(membershipRepository);
        var communities = new CommunityLookup(communityStore);
        var communityService = new CommunityService(communityStore,
                new JdbcCommunityKindStore(mock(CommunityKindRepository.class)), membershipLedger, communities,
                mock(MembershipAuthorizer.class), mock(CurrentUser.class), organizationContext,
                new MediaLinks("https://media.example.test"), mock(AuthorizationGate.class),
                mock(CommunityCountRule.class), mock(ApplicationEventPublisher.class),
                new UsageCounters(new SimpleMeterRegistry()));
        var communityPolicyService = new CommunityPolicyService(communityStore, communities,
                mock(MembershipAuthorizer.class), mock(CurrentUser.class), organizationContext);
        var membershipService = new MembershipService(communityStore, membershipLedger,
                mock(MembershipAuthorizer.class), mock(CurrentUser.class), organizationContext,
                mock(ApplicationEventPublisher.class), mock(OrganizationOwners.class),
                mock(dev.axonia.community.application.MemberDirectoryService.class));
        // events' own OrganizationContext is unused by the code paths this fixture exercises - only the org-explicit
        // CommunityAccess overloads are reached from MessageSubjectPolicyAdapter - so an unstubbed mock is enough.
        var communityAccess = new CommunityAccessAdapter(communityService, communityPolicyService,
                mock(CommunityCapabilities.class), membershipService, mock(MemberDirectory.class),
                mock(dev.axonia.events.application.port.out.OrganizationContext.class), mock(ChatDestinations.class));
        return new MessageSubjectPolicyAdapter(new SessionSubjectResolver(sessionRepository), communityAccess);
    }

    /** community's real, throwing {@code OrganizationContext} - not a mock - is the entire point of this fixture. */
    private static OrganizationContext communityOrganizationContext() {

        return CurrentToken::organization;
    }
}
