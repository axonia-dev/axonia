package dev.axonia;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import dev.axonia.communication.api.DeliveryOutcome;
import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerSettings;
import dev.axonia.communication.api.MailServerState;
import dev.axonia.communication.api.spi.MailServer;
import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.identity.api.spi.IdentityAccounts;
import dev.axonia.identity.api.spi.OrganizationCardLookSource;
import dev.axonia.identity.api.spi.OrganizationIdpResolver;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.identity.api.spi.SignInObserver;
import dev.axonia.identity.api.spi.SignInProviderResolver;
import dev.axonia.identity.api.spi.SuccessionAddresses;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.organization.api.Organizations;
import dev.axonia.shared.api.paid.PaidAction;
import dev.axonia.shared.api.paid.PaidCapability;
import dev.axonia.shared.api.security.CurrentToken;
import dev.axonia.shared.api.tenancy.OrganizationScope;

/**
 * One bean per open port that only closed code implements, each answering what the Cloud adapter answers for fixtures
 * that hold no closed rows: no SSO connection, no succession, no Hub card, no Standing and no identity-provider account
 * to delete. A test that needs another answer mocks the port with {@code @MockitoBean}, or runs in the Cloud build.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ClosedPortStandIns {

    private static final Map<String, String> SOCIAL_PROVIDERS = Map.of("google", "Google", "github", "GitHub");

    @Bean
    SuccessionAddresses successionAddressesStandIn() {

        return (organizationId, arrivingAddress) -> Optional.empty();
    }

    @Bean
    SignInProviderResolver signInProviderResolverStandIn() {

        return organizationId -> {
            var alias = CurrentToken.claim("identity_provider");
            return alias == null ? null : SOCIAL_PROVIDERS.get(alias.toLowerCase(Locale.ROOT));
        };
    }

    @Bean
    SignInObserver signInObserverStandIn() {

        return new SignInObserver() {

            @Override
            public void beforeProvisioning(IdentityClaims claims) {
                // no succession is declared without a closed SSO connection
            }

            @Override
            public void afterProvisioning(IdentityClaims claims) {
                // no Entra tenant is proved without a closed SSO connection
            }

            @Override
            public void signedIn(IdentityClaims claims) {
                // platform accounts are closed
            }
        };
    }

    @Bean
    PaidCapability paidCapabilityStandIn(Organizations organizations) {

        return new PaidCapability() {

            @Override
            public boolean isOpenTo(String organizationId, String subject) {

                return organizations.byId(organizationId)
                        .filter(organization -> organization.kind() == OrganizationKind.ENTERPRISE).isPresent();
            }

            @Override
            public boolean isOpenToCaller() {

                return CurrentToken.hasOrganization() && isOpenTo(CurrentToken.organization(), CurrentToken.subject());
            }

            @Override
            public String refusal(PaidAction action) {

                return "Not open to this account.";
            }
        };
    }

    @Bean
    OrganizationMembershipSource organizationMembershipSourceStandIn() {

        return subject -> List.of();
    }

    @Bean
    OrganizationIdpResolver organizationIdpResolverStandIn() {

        return organizationId -> Optional.empty();
    }

    @Bean
    OrganizationCardLookSource organizationCardLookSourceStandIn(OrganizationRegistry organizationService) {

        return new OrganizationCardLookSource() {

            @Override
            public Map<String, CardLook> lookFor(Collection<String> organizationIds) {

                return Map.of();
            }

            @Override
            public Map<String, String> kindFor(Collection<String> organizationIds) {

                return organizationIds.stream()
                        .flatMap(id -> OrganizationScope.runAs(id, () -> organizationService.organization(id))
                                .map(organization -> Map.entry(id, organization.kind().name())).stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        };
    }

    @Bean
    IdentityAccounts identityAccountsStandIn() {

        return subject -> {
            // no Keycloak account to delete
        };
    }

    @Bean
    MailServer mailServerStandIn() {

        var local = MailServerState.configured(MailServerSettings.builder().host("localhost").port(1025)
                .security(MailSecurity.NONE).fromAddress("axonia@localhost").build());
        return new MailServer() {

            @Override
            public MailServerState current() {

                return local;
            }

            @Override
            public void recordOutcome(DeliveryOutcome outcome) {
                // the Cloud's adapter ignores outcomes too
            }
        };
    }
}
