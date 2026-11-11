package dev.axonia.selfhosted.adapter.out.access;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.spi.OwnershipAnchorPolicy;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;

/**
 * The installation's Organization is owned through the configured owner group, which every start binds to the Owner
 * role again, so its operator can always restore ownership whatever is revoked inside the product.
 */
@Component
class ConfiguredOwnerGroupAnchor implements OwnershipAnchorPolicy {

    private final String organizationId;

    ConfiguredOwnerGroupAnchor(SelfHostedProperties properties) {
        this.organizationId = properties.organization().id();
    }

    @Override
    public Optional<Boolean> anchoredOutsideTheProduct(String organizationId) {
        return this.organizationId.equals(organizationId) ? Optional.of(true) : Optional.empty();
    }
}
