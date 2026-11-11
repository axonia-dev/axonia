package dev.axonia.selfhosted.adapter.out.identity;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.GroupsClaimSource;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;

/**
 * The installation's groups are read from whichever claim the operator configured, and only {@code groups} lists the
 * person's directory groups: any other, such as Entra's {@code roles}, carries values the operator assigns instead.
 */
@Component
class ConfiguredGroupsClaimSource implements GroupsClaimSource {

    private static final String DIRECTORY_GROUPS_CLAIM = "groups";

    private final String organizationId;

    private final boolean readsAnotherClaim;

    ConfiguredGroupsClaimSource(SelfHostedProperties properties) {
        this.organizationId = properties.organization().id();
        this.readsAnotherClaim = !DIRECTORY_GROUPS_CLAIM.equals(properties.oidc().groupsClaim());
    }

    @Override
    public boolean readsAnotherClaim(String organizationId) {
        return readsAnotherClaim && this.organizationId.equals(organizationId);
    }
}
