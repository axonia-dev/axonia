package dev.axonia.selfhosted.internal;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationScope;
import dev.axonia.selfhosted.SelfHostedProperties;
import lombok.RequiredArgsConstructor;

/**
 * Founds the installation's Organization at the first start and holds every later start to it. It runs once the
 * application is ready and before it reports ready for traffic, so a refusal stops the start and no traffic is sent to
 * an installation whose configuration contradicts its data.
 */
@Component
@RequiredArgsConstructor
class SelfHostedOrganization {

    private final SelfHostedProperties properties;
    private final SelfHostedFounding founding;

    @EventListener(ApplicationReadyEvent.class)
    void establishOnStart() {

        establish(Installation.configuredBy(properties));
    }

    void establish(Installation installation) {

        OrganizationScope.runAs(installation.organizationId(), () -> founding.establish(installation));
    }
}
