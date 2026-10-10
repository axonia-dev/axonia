package dev.axonia.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.DirectoryGroupKeys;
import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.spi.DirectoryGroupClaims;
import dev.axonia.identity.application.port.out.DirectoryGroupStore;
import dev.axonia.identity.domain.DirectoryGroup;
import lombok.RequiredArgsConstructor;

/**
 * Realigns an Organization's SCIM-synced directory groups with its resolved IdP provider's reconciliation-key
 * convention: the external object id for Entra, the display name otherwise.
 *
 * <p>
 * The key is written per SCIM write from the provider resolved at that moment, so an Organization that provisions
 * groups before creating its SSO connection, or that changes provider afterwards, ends up holding both conventions at
 * once. That mixed state is worse than a wholly wrong one: the authorization fallback returns a non-empty list built
 * from both eras, so the caller gets partial access, and the silent-denial warning only fires on an entirely empty
 * list. Rewriting on every provider write keeps one Organization to one convention.
 *
 * <p>
 * Both source columns are already stored, so this is a local UPDATE and needs no call to the IdP.
 */
@Component
@RequiredArgsConstructor
public class DirectoryGroupRekeyer implements DirectoryGroupKeys {

    private static final Logger log = LoggerFactory.getLogger(DirectoryGroupRekeyer.class);

    private final DirectoryGroupStore store;
    private final DirectoryGroupClaims directoryGroupClaims;
    private final Clock clock;

    /**
     * One transaction, because holding the groups that need it and moving the keys are two statements describing one
     * change: a rekey committed without its holds is the escalation this guard exists to stop.
     */
    @Override
    @Transactional
    public void rekeyTo(String organizationId, IdpProvider provider) {

        if (provider.federatesMicrosoftDirectory()) {
            holdGroupsLandingOnAClaimedKey(organizationId,
                    store.findConfirmedGroupsRekeyingToExternalId(organizationId), DirectoryGroup::externalId);
            warnOnGroupsThatCannotCarryAnEntraKey(organizationId);
            log.info("Rewrote {} directory group reconciliation keys to externalId for Organization {} (provider {})",
                    store.rekeyToExternalId(organizationId), organizationId, provider);
        } else {
            holdGroupsLandingOnAClaimedKey(organizationId,
                    store.findConfirmedGroupsRekeyingToDisplayName(organizationId), DirectoryGroup::displayName);
            log.info("Rewrote {} directory group reconciliation keys to displayName for Organization {} (provider {})",
                    store.rekeyToDisplayName(organizationId), organizationId, provider);
        }
    }

    /**
     * Takes the confirmation back off the groups this rekey moves onto a key some role binding is already waiting on.
     *
     * <p>
     * A confirmation vouches for a group under one key and says nothing about another, so without this a group
     * confirmed under a key nobody has bound would arrive on a claimed key still confirmed after a provider switch.
     *
     * <p>
     * Only the claimed landings are held, matching the rule {@code DirectoryGroupHold} applies to a SCIM rename.
     * Clearing every moved row instead would hold an Organization's entire directory on a provider switch, make every
     * group demand a confirmation nothing was ever waiting for, and leave a group nobody had bound releasable only by
     * binding a role to it first.
     *
     * <p>
     * Whether a key is claimed is a fact about {@code role_binding}, which belongs to another module. Expressing this
     * as one set-based statement would mean joining that table from {@code identity}'s own SQL - the exact dependency
     * {@link DirectoryGroupClaims} exists to invert, and one {@code ModularityTests} could not catch, since it verifies
     * types rather than query text. So the movers are selected by the same {@code WHERE} clause the rewrite uses, asked
     * about through the port, and held by id in one bounded statement inside the same transaction.
     */
    private void holdGroupsLandingOnAClaimedKey(String organizationId, List<DirectoryGroup> movers,
            Function<DirectoryGroup, String> newKey) {

        var held = movers.stream()
                .filter(group -> directoryGroupClaims.isClaimedByARoleBinding(organizationId, newKey.apply(group)))
                .map(DirectoryGroup::id).toList();
        if (held.isEmpty()) {
            return;
        }
        log.warn("Held {} directory groups in Organization {}: the provider switch moves them onto reconciliation keys "
                + "a role binding is already waiting on, so each needs confirming again before it confers anything.",
                held.size(), organizationId);
        store.holdByIds(organizationId, held, clock.instant());
    }

    private void warnOnGroupsThatCannotCarryAnEntraKey(String organizationId) {

        var stranded = store.countGroupsWithoutExternalId(organizationId);
        if (stranded > 0) {
            log.warn("Organization {} has {} directory groups with no externalId, so their reconciliation key keeps "
                    + "its display-name value and will not match the object ids an Entra groups claim carries. Roles "
                    + "bound to those groups will not resolve until SCIM re-provisions them with an externalId.",
                    organizationId, stranded);
        }
    }
}
