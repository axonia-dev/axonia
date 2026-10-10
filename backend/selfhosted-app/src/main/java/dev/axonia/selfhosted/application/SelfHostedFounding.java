package dev.axonia.selfhosted.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AccessSeeding;
import dev.axonia.access.api.OwnerBinding;
import dev.axonia.founding.api.FoundingRequest;
import dev.axonia.founding.api.OrganizationFounder;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.selfhosted.application.port.out.FoundingLedger;
import dev.axonia.selfhosted.domain.Founded;
import dev.axonia.selfhosted.domain.Installation;
import dev.axonia.selfhosted.domain.SubjectClaimLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The founding step of every start, in one transaction under the founding lock: found the Organization when the
 * installation has none; otherwise refuse a configuration naming another Organization or subject claim, and bring the
 * name and the owner group up to the configuration without removing any binding. A bean of its own so that
 * {@code SelfHostedOrganization} enters the Organization's scope before this transaction opens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfHostedFounding {

    private static final String OWNERS_LABEL = "Owners";

    private final FoundingLedger settings;
    private final OrganizationFounder founder;
    private final AccessSeeding accessSeeder;
    private final OrganizationRegistry organizations;
    private final Clock clock;

    @Transactional
    public void establish(Installation installation) {

        settings.serializeFounding();
        settings.find().ifPresentOrElse(founded -> keep(founded, installation), () -> found(installation));
    }

    private void found(Installation installation) {

        founder.found(new FoundingRequest(installation.organizationId(), installation.organizationName(),
                owners(installation)));
        settings.record(new Founded(installation.organizationId(), installation.subjectClaim(),
                clock.instant().truncatedTo(ChronoUnit.MILLIS)));
        log.info("Founded Organization {} and bound its Owner role to directory group {}",
                installation.organizationId(), installation.ownerGroup());
    }

    private void keep(Founded founded, Installation installation) {

        if (!founded.organizationId().equals(installation.organizationId())) {
            throw new IllegalStateException(("The installation was founded as Organization '%s', and "
                    + "axonia.selfhosted.organization.id now names '%s'. Every row is stored under the first, so set "
                    + "it back to '%s'.")
                    .formatted(founded.organizationId(), installation.organizationId(), founded.organizationId()));
        }
        SubjectClaimLock.verify(founded.subjectClaim(), installation.subjectClaim());
        var registered = organizations.organization(installation.organizationId()).orElseThrow(
                () -> new IllegalStateException("The installation's Organization '%s' is missing from the registry."
                        .formatted(installation.organizationId())));
        if (!registered.name().equals(installation.organizationName())) {
            organizations.rename(installation.organizationId(), installation.organizationName());
            log.info("Renamed Organization {} to {}", installation.organizationId(), installation.organizationName());
        }
        if (accessSeeder.ensureGroupOwner(installation.organizationId(), owners(installation))) {
            log.info("Bound the Owner role of Organization {} to directory group {}; earlier owner groups keep it",
                    installation.organizationId(), installation.ownerGroup());
        }
    }

    private static OwnerBinding.Group owners(Installation installation) {

        return new OwnerBinding.Group(installation.ownerGroup(), OWNERS_LABEL);
    }
}
