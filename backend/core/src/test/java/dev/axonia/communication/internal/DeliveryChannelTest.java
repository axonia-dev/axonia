package dev.axonia.communication.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryChannelTest {

    /** Named, not inferred from the other channels, so a channel added later is not mail until it says so. */
    @Test
    void isMailOnlyForMailAndForARowWithNoChannel() {

        // when / then
        assertThat(DeliveryChannel.isMail("EMAIL")).isTrue();
        assertThat(DeliveryChannel.isMail(null)).isTrue();
        assertThat(DeliveryChannel.isMail("CHAT")).isFalse();
        assertThat(DeliveryChannel.isMail("IN_APP")).isFalse();
        assertThat(DeliveryChannel.isMail("PUSH")).isFalse();
    }
}
