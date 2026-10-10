package dev.axonia.communication.domain;

import java.util.Optional;

import dev.axonia.communication.api.ChatChannelStatus;
import dev.axonia.communication.api.SuppressionReason;

/**
 * The one place a {@link ChatChannelStatus} becomes a {@link SuppressionReason} - or does not, because the destination
 * is {@link ChatChannelStatus.Ready}. {@code DeliveryGate#admitChat} and {@code OutboxRowProcessor#sendToDestination}
 * each read a fresh status at a different moment (admission time and send time respectively - see both callers' own
 * javadoc for why neither trusts the other's read), but the two must still agree on what a broken or unconfigured
 * destination means: an operator reading {@code NO_DESTINATION} goes to add a destination, and one reading
 * {@code DESTINATION_BROKEN} goes to reconnect one that already is configured. Copying this switch into both callers
 * would let that agreement drift, and CPD is a hard gate on main sources here regardless.
 */
public final class ChatChannelSuppression {

    private ChatChannelSuppression() {
    }

    public static Optional<SuppressionReason> forStatus(ChatChannelStatus status) {

        return switch (status) {
            case ChatChannelStatus.NotConfigured ignored -> Optional.of(SuppressionReason.NO_DESTINATION);
            case ChatChannelStatus.Broken ignored -> Optional.of(SuppressionReason.DESTINATION_BROKEN);
            case ChatChannelStatus.Ready ignored -> Optional.empty();
        };
    }
}
