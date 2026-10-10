package dev.axonia.selfhosted.adapter.out.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoSuccessionAddressesTest {

    @Test
    void noArrivingAddressSucceedsAnother() {

        // when
        var actual = new NoSuccessionAddresses().formerAddressOf("acme", "alice@acme.test");

        // then
        assertThat(actual).isEmpty();
    }
}
