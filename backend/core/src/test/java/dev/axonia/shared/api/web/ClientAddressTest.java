package dev.axonia.shared.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    @Test
    void readsTheAddressOurEdgeStamped() {

        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader(ClientAddress.HEADER, "203.0.113.7");

        assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.7");
        assertThat(ClientAddress.isStamped(request)).isTrue();
    }

    @Test
    void fallsBackToTheSocketAddressWhenNothingWasStamped() {

        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientAddress.of(request)).isEqualTo("10.0.0.1");
        assertThat(ClientAddress.isStamped(request)).isFalse();
    }

    @Test
    void treatsABlankStampAsAbsent() {

        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader(ClientAddress.HEADER, "   ");

        assertThat(ClientAddress.of(request)).isEqualTo("10.0.0.1");
        assertThat(ClientAddress.isStamped(request)).isFalse();
    }

    @Test
    void ignoresXForwardedForEntirely() {

        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7");

        assertThat(ClientAddress.of(request)).isEqualTo("10.0.0.1");
    }
}
