package dev.axonia.events.internal;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.CurrentToken;
import dev.axonia.OrganizationScope;
import dev.axonia.UsageCounters;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.RoleBindingService;
import dev.axonia.communication.MessageType;
import dev.axonia.community.ChatDestinationService;
import dev.axonia.community.CommunityCapabilities;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.CurrentUser;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.MembershipService;
import dev.axonia.community.OrganizationContext;
import dev.axonia.community.internal.Communities;
import dev.axonia.community.internal.CommunityCountRule;
import dev.axonia.community.internal.CommunityKindRepository;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.internal.MembershipAuthorizer;
import dev.axonia.community.internal.MembershipRepository;
import dev.axonia.community.mother.CommunityEntityMother;
import dev.axonia.community.mother.MembershipEntityMother;
import dev.axonia.events.mother.SessionEntityMother;
import dev.axonia.media.MediaLinks;
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
 * This wires the REAL {@link CommunityService} and {@link MembershipService} - with the REAL, throwing
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
        var communities = new Communities(communityRepository);
        var communityService = new CommunityService(communityRepository, mock(CommunityKindRepository.class),
                membershipRepository, communities, mock(MembershipAuthorizer.class), mock(CurrentUser.class),
                organizationContext, new MediaLinks("https://media.example.test"), mock(AuthorizationGate.class),
                mock(CommunityCountRule.class), mock(ApplicationEventPublisher.class),
                new UsageCounters(new SimpleMeterRegistry()));
        var communityPolicyService = new CommunityPolicyService(communityRepository, communities,
                mock(MembershipAuthorizer.class), mock(CurrentUser.class), organizationContext);
        var membershipService = new MembershipService(communityRepository, membershipRepository,
                mock(MembershipAuthorizer.class), mock(CurrentUser.class), organizationContext,
                mock(ApplicationEventPublisher.class), mock(RoleBindingService.class), mock(MemberDirectory.class));
        // events' own OrganizationContext is unused by the code paths this fixture exercises - only the org-explicit
        // CommunityAccess overloads are reached from MessageSubjectPolicyAdapter - so an unstubbed mock is enough.
        var communityAccess = new CommunityAccessAdapter(communityService, communityPolicyService,
                mock(CommunityCapabilities.class), membershipService, mock(MemberDirectory.class),
                mock(dev.axonia.events.OrganizationContext.class), mock(ChatDestinationService.class));
        return new MessageSubjectPolicyAdapter(new SessionSubjectResolver(sessionRepository), communityAccess);
    }

    /** community's real, throwing {@code OrganizationContext} - not a mock - is the entire point of this fixture. */
    private static OrganizationContext communityOrganizationContext() {

        return CurrentToken::organization;
    }
}
