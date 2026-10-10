package dev.axonia.selfhosted.adapter.in.event;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.selfhosted.application.SelfHostedFounding;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;
import dev.axonia.selfhosted.domain.Installation;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import lombok.RequiredArgsConstructor;

/**
 * Founds the installation's Organization at the first start and holds every later start to it. It runs once the
 * application is ready and before it reports ready for traffic, so a refusal stops the start and no traffic is sent to
 * an installation whose configuration contradicts its data.
 */
@Component
@RequiredArgsConstructor
public class SelfHostedOrganization {

    private final SelfHostedProperties properties;
    private final SelfHostedFounding founding;

    @EventListener(ApplicationReadyEvent.class)
    void establishOnStart() {

        establish(configuredBy(properties));
    }

    public void establish(Installation installation) {

        OrganizationScope.runAs(installation.organizationId(), () -> founding.establish(installation));
    }

    private static Installation configuredBy(SelfHostedProperties properties) {

        return new Installation(properties.organization().id(), properties.organization().name(),
                properties.ownerGroup(), properties.oidc().subjectClaim());
    }
}
