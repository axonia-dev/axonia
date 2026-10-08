package dev.axonia.selfhosted.internal;

import dev.axonia.selfhosted.SelfHostedProperties;

/** What the configuration says the installation is. */
record Installation(String organizationId, String organizationName, String ownerGroup, String subjectClaim) {

    static Installation configuredBy(SelfHostedProperties properties) {

        return new Installation(properties.organization().id(), properties.organization().name(),
                properties.ownerGroup(), properties.oidc().subjectClaim());
    }
}
