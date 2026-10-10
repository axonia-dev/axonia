package dev.axonia.communication.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.SuppressionReason;
import dev.axonia.communication.api.spi.ChatDestinationsPort;
import dev.axonia.communication.api.spi.MailServer;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.communication.application.port.out.NotificationPreferenceStore;
import dev.axonia.communication.domain.ChatChannelSuppression;
import dev.axonia.communication.domain.DeliveryChannel;
import dev.axonia.communication.domain.OutboxDelivery;
import dev.axonia.communication.domain.StoredPreference;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.Organizations;
import lombok.RequiredArgsConstructor;

/**
 * For a mail row, whether there is a mail server at all; then four checks, widest to narrowest, run at drain rather
 * than at fan-out: the organization kill switch, whether whatever the message is about has this type switched off - a
 * community for a session, the organization itself for a roundup - the member's own preference, and whether the member
 * is still a member of whatever the subject belongs to, which for an organization-scoped subject means still present in
 * its directory. Each short-circuits the rest - a row failing the first check never touches the others - and the first
 * one that fails is the row's {@link SuppressionReason}.
 *
 * <p>
 * {@link SuppressionReason#MAIL_NOT_CONFIGURED} is the mail twin of chat's {@link SuppressionReason#NO_DESTINATION}: it
 * is checked before the kill switch, because with no server nothing any setting says can send, and never for a post or
 * an entry, which need none.
 *
 * <p>
 * The second and fourth checks are answered by the {@link MessageSubjectPolicy} that claims the row's
 * {@link MessageType}, since only the module owning that kind of subject can read it. {@link MessageSubjectPolicies}
 * refuses to start with a declared type no policy claims.
 *
 * <p>
 * Checking at drain rather than at fan-out is the point: up to 48 hours of grace period can pass between the two, and a
 * member who unsubscribes inside that window must not receive the message they just unsubscribed from.
 *
 * <p>
 * The organization kill switch is checked before the row's {@link MessageType} is even resolved, and resolution uses
 * {@link MessageType#parse} and {@link MessageSubjectPolicies#policyFor}, which return empty rather than throwing: a
 * row written by an older deploy can carry a message type code this edition no longer sends, and an organization that
 * switched communications off must still be able to silence such a row rather than have the gate blow up on it before
 * that widest check ever runs. An unrecognized type is then suppressed as {@link SuppressionReason#UNKNOWN_TYPE} - no
 * setting turned anything off, the type itself predates this deploy, and recording it as a subject opt-out would send
 * an operator chasing a setting that was never the cause. That widest check does not apply to an
 * {@link DeliveryChannel#IN_APP} row, so for that channel alone {@link #admit} resolves the type - and can therefore
 * return {@link SuppressionReason#UNKNOWN_TYPE} - before the organization check would otherwise run: there is no kill
 * switch left above it to shadow.
 *
 * <p>
 * A chat row is a second track through this gate, not a detail of one method. It still passes the two widest checks -
 * the organization kill switch and the subject's own type switch - but {@link #admit} then dispatches it to
 * {@link #admitChat} instead of {@link #admitMember}: there is no member to read a preference or a membership from, so
 * the two narrowest checks are replaced outright by {@link ChatDestinationsPort#destinationsFor} and
 * {@link ChatDestinationsPort#statusFor}. {@link SuppressionReason#NO_DESTINATION} and
 * {@link SuppressionReason#DESTINATION_BROKEN} are that branch's own outcomes, in place of
 * {@link SuppressionReason#MEMBER_OPTED_OUT} and {@link SuppressionReason#NOT_A_MEMBER}.
 *
 * <p>
 * An in-app row is a third track, dispatched to {@link #admitInApp} instead. It keeps the subject's own type switch and
 * the membership check but skips both the organization kill switch and the member's own preference - see
 * {@link #admitInApp}'s own javadoc for why - so it is judged by a different two of the four layers than either mail or
 * chat.
 */
@Component
@RequiredArgsConstructor
public final class DeliveryGate {

    private final MailServer mailServer;
    private final Organizations organizations;
    private final MessageSubjectPolicies policies;
    private final ChatDestinationsPort chatDestinations;
    private final NotificationPreferenceStore notificationPreferenceStore;

    Optional<SuppressionReason> admit(OutboxDelivery row) {

        if (isMailWithNoServer(row)) {
            return Optional.of(SuppressionReason.MAIL_NOT_CONFIGURED);
        }
        var inApp = DeliveryChannel.IN_APP.name().equals(row.getChannel());
        var organizationId = row.getOrganizationId();

        if (!inApp && !organizationAllows(organizationId)) {
            return Optional.of(SuppressionReason.ORGANIZATION_OFF);
        }

        var type = MessageType.parse(row.getMessageType());
        var policy = type.flatMap(policies::policyFor);
        if (policy.isEmpty()) {
            return Optional.of(SuppressionReason.UNKNOWN_TYPE);
        }
        if (!policy.get().isEnabledFor(organizationId, type.get(), row.getSubjectRef())) {
            return Optional.of(SuppressionReason.SUBJECT_OFF);
        }
        if (inApp) {
            return admitInApp(row, policy.get());
        }
        return DeliveryChannel.CHAT.name().equals(row.getChannel())
                ? admitChat(row)
                : admitMember(row, policy.get(), type.get());
    }

    private boolean isMailWithNoServer(OutboxDelivery row) {

        return DeliveryChannel.isMail(row.getChannel())
                && mailServer.current() instanceof MailServerState.NotConfigured;
    }

    /**
     * Two of the four layers, and a different two from {@link #admitChat}'s. The organization kill switch is skipped
     * because it stops axonia CONTACTING an organization's people, and an entry inside a product they are already
     * signed into contacts nobody. The member's own preference is skipped because it is a preference about mail: a
     * member who has silenced a kind of mail still has a complete record, which is the whole reason silencing it is
     * safe.
     *
     * <p>
     * The two it keeps are the two that answer whether this message should exist for this person at all. A community
     * that switched wrap-ups off is not sending wrap-ups, so there is nothing to record; somebody who left during a
     * reminder's lead time is owed no entry about it.
     */
    private Optional<SuppressionReason> admitInApp(OutboxDelivery row, MessageSubjectPolicy policy) {

        return policy.isStillAMember(row.getOrganizationId(), row.getSubjectRef(), row.getUserId())
                ? Optional.empty()
                : Optional.of(SuppressionReason.NOT_A_MEMBER);
    }

    /**
     * Two of the four layers, and that is all there is. The two it skips are skipped because they are meaningless on a
     * room rather than because they are inconvenient: nobody subscribed to a channel, so there is no preference to
     * honour and no membership to check.
     *
     * <p>
     * A placeholder row - no {@code chatDestinationId} - is not a post. It is admitted so {@code OutboxRowProcessor}
     * can expand it, and suppressed only when the community has nowhere to post at all, which is the same
     * {@link SuppressionReason#NO_DESTINATION} an expanded row gets when its destination is removed underneath it. An
     * expanded row names exactly one destination, and is judged on that destination's own status.
     */
    private Optional<SuppressionReason> admitChat(OutboxDelivery row) {

        if (row.getChatDestinationId() == null) {
            return chatDestinations.destinationsFor(row.getOrganizationId(), row.getSubjectRef()).isEmpty()
                    ? Optional.of(SuppressionReason.NO_DESTINATION)
                    : Optional.empty();
        }
        return ChatChannelSuppression
                .forStatus(chatDestinations.statusFor(row.getOrganizationId(), row.getChatDestinationId()));
    }

    private Optional<SuppressionReason> admitMember(OutboxDelivery row, MessageSubjectPolicy policy, MessageType type) {

        if (!memberAllows(row, type)) {
            return Optional.of(SuppressionReason.MEMBER_OPTED_OUT);
        }
        if (!policy.isStillAMember(row.getOrganizationId(), row.getSubjectRef(), row.getUserId())) {
            return Optional.of(SuppressionReason.NOT_A_MEMBER);
        }
        return Optional.empty();
    }

    private boolean organizationAllows(String organizationId) {

        return organizations.byId(organizationId).map(Organization::communicationsEnabled).orElse(false);
    }

    private boolean memberAllows(OutboxDelivery row, MessageType type) {

        return notificationPreferenceStore.storedFor(row.getOrganizationId(), row.getUserId(), type)
                .map(StoredPreference::enabled).orElse(true);
    }
}
