package dev.axonia.roundup.adapter.out.communication;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.communication.api.ProductionWatermark;
import dev.axonia.communication.api.spi.MessageProducer;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.Organizations;
import dev.axonia.roundup.application.RoundupAvailability;
import dev.axonia.roundup.application.RoundupFactsAssembler;
import dev.axonia.roundup.application.RoundupPreviewer;
import dev.axonia.roundup.application.RoundupSettingsService;
import dev.axonia.roundup.configuration.RoundupProperties;
import dev.axonia.roundup.domain.Quarter;
import dev.axonia.roundup.domain.RoundupFacts;
import dev.axonia.shared.api.tenancy.OrganizationResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides, on every produce tick, whether an organization owes a roundup for the quarter that has just closed, and
 * addresses it to every present member of its directory when it does. Unlike a session message, nobody selected these
 * recipients by joining anything: the roundup is one organization-wide letter, so its recipient list IS the directory.
 *
 * <p>
 * The guards run cheapest first, so an ordinary tick - which for any given organization is 24,000 ticks of nothing per
 * quarter - costs the availability question, a registry lookup, one settings read and one watermark read before it
 * returns. In order: the roundup is available to the organization at all (see
 * {@link dev.axonia.roundup.api.spi.RoundupAvailabilityPolicy}: one with no provisioned directory to address has no
 * recipient list to build), asked first because an organization it is not available to then stops without a registry
 * lookup of its own; the organization resolves; its roundup is switched on; the quarter has settled; and the watermark
 * has not already claimed it.
 *
 * <p>
 * <b>The organization-wide communications kill switch is deliberately not among them.</b> That switch stops what leaves
 * the product, and only {@code DeliveryGate} can stop that: it suppresses this roundup's {@code EMAIL} rows as
 * {@code ORGANIZATION_OFF} while admitting its {@code IN_APP} ones, which is the design rather than an oversight. A
 * tenant that has silenced outbound mail is precisely the tenant where the in-app entry is the only record there is, so
 * declining to enqueue would leave its members nothing - and leave it unexplained, an absence rather than a suppressed
 * row naming its own reason, which every other message type does record. It would also not cost that tenant one
 * quarter: the watermark advances on this tick regardless, so a quarter enqueued while the switch was off is claimed
 * exactly once, whereas returning above it left the watermark behind and made every silenced quarter permanently
 * unreachable the moment the switch came back on.
 *
 * <p>
 * A quarter below the activity floor - no session the letter can still name - advances the watermark and enqueues
 * nothing. That is what makes a dead quarter skipped once rather than re-derived every five minutes for the three
 * months it stays the most recently closed one.
 *
 * <p>
 * <b>The floor is asked of the assembled letter, not of the reading it was assembled from.</b> The message is built
 * first and {@link RoundupFacts#isBelowActivityFloor} then judges the very figures it states. That ordering is the
 * whole guarantee: {@link RoundupFacts#of} drops every session whose community {@code activeCommunities} no longer
 * names, so a tenant that archived the one community which held everything last quarter reads as busy at the port and
 * as empty in the letter, and a floor measured on the raw port result would mail an entire enterprise a letter opening
 * "No sessions were held over the quarter." Reading it once, from the same place, is what makes it impossible for the
 * floor and the message to disagree about what the quarter contained. The floor is now one clause, {@code
 * sessionCount == 0}, where it used to be two, no session AND nothing captured - and the second clause is redundant now
 * rather than dropped. Capture is counted through the quarter's own held sessions, not through an artifact's own
 * creation date, so {@code resourceCount} cannot be anything but zero once {@code sessionCount} already is: a clause
 * that can never turn a passing letter into a failing one adds nothing a reader of this method could tell apart from
 * the first.
 *
 * <p>
 * The facts it enqueues are the quarter's real ones, assembled through {@link RoundupFacts#of} - the same call
 * {@link RoundupPreviewer} makes for a quarter with nothing enqueued yet, and the same single pass the floor above was
 * read from. They are frozen into the outbox row at this instant and are what every recipient will eventually be shown.
 *
 * <p>
 * A quarter that cleared the floor but reached an empty directory is logged rather than passed over in silence. The
 * watermark advances there too - it must, or every subsequent tick re-derives the same quarter - so that quarter is
 * skipped once and permanently, and this is the only trace it leaves. Usually it means what it says, a tenant with
 * nobody present; but it is also the exact shape an {@code identity_user} scoping fault takes while
 * {@code community_session} and {@code artifact} still answer normally, and the {@link OrganizationResolver} guard
 * below cannot see that one, because it reads the {@code SecurityContext} rather than the GUC the connection was
 * actually given.
 *
 * <p>
 * {@code produce} carries its own {@code @Transactional} for the reason {@code WrapUpProducer}'s class javadoc gives:
 * the enqueue and the {@link ProductionWatermark#advanceTo} that follows it must commit together or not at all.
 * Producers run outside any tick-wide transaction, so nothing above this method opens one on its behalf, and a crash
 * that let {@code advanceTo} commit without the enqueue would skip the quarter permanently.
 *
 * <p>
 * {@code organizationId} comes from the shared-kernel {@link OrganizationResolver}, not from this module's JWT-bound
 * {@code OrganizationContext}, for the reason {@code UpcomingSessionProducer}'s class javadoc gives: this runs inside
 * the sweep's {@code OrganizationScope.runAs}, which carries no JWT. That is also the one guard the activity ports
 * behind {@link RoundupFactsAssembler} cannot supply themselves. Every table they read - {@code community_session},
 * {@code artifact}, {@code identity_user} - is row-level-secured on {@code app.current_org}, and under the
 * {@code axonia_app} role an unscoped read answers an empty list or a zero rather than failing. Resolving the
 * organization from the very scope that sets that GUC means an unscoped tick throws here, before it can read a dead
 * quarter and advance the watermark past it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoundupProducer implements MessageProducer {

    static final String PRODUCER_KEY = "organization-roundup";

    private static final String TEMPLATE = "organization-roundup";

    private final OrganizationResolver organizationResolver;
    private final Organizations organizations;
    private final RoundupAvailability availability;
    private final RoundupSettingsService settings;
    private final RoundupFactsAssembler assembler;
    private final UserDirectory directory;
    private final CommunicationPort port;
    private final ProductionWatermark watermark;
    private final RoundupProperties properties;

    @Override
    @Transactional
    public void produce(Instant now) {

        var organizationId = organizationId();
        if (!availability.in(organizationId)) {
            return;
        }
        var organization = organizations.byId(organizationId).orElse(null);
        if (organization == null) {
            return;
        }
        if (!settings.enabledFor(organizationId)) {
            return;
        }
        var quarter = Quarter.mostRecentlyClosed(now);
        if (now.isBefore(quarter.endExclusive().plus(properties.settleLag()))) {
            return;
        }
        if (!watermark.since(PRODUCER_KEY, Instant.EPOCH).isBefore(quarter.endExclusive())) {
            return;
        }
        enqueueIfAnythingHappened(organization, quarter);
        watermark.advanceTo(PRODUCER_KEY, now);
    }

    private void enqueueIfAnythingHappened(Organization organization, Quarter quarter) {

        var organizationId = organization.id();
        var facts = RoundupFacts.of(assembler.read(organizationId, organization.name(), quarter));
        if (RoundupFacts.isBelowActivityFloor(facts)) {
            return;
        }
        var recipients = directory.presentUserIds(organizationId);
        if (recipients.isEmpty()) {
            log.warn("Roundup {} for organization {} reached nobody: the directory reports no present member",
                    quarter.key(), organizationId);
            return;
        }
        port.enqueue(OutboundMessage.builder().type(MessageType.ORGANIZATION_ROUNDUP).subjectRef(quarter.key())
                .recipientUserIds(recipients).template(TEMPLATE).facts(facts).build());
    }

    private String organizationId() {

        return organizationResolver.currentOrganization()
                .orElseThrow(() -> new IllegalStateException("RoundupProducer must run inside an Organization scope"));
    }
}
