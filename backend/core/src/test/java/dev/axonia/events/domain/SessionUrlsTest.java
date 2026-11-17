package dev.axonia.events.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionUrlsTest {

    @Test
    void communityUrlCarriesTheOrganization() {

        var actual = SessionUrls.communityUrl("https://axonia.example", "dojo", "acme");

        assertThat(actual).isEqualTo("https://axonia.example/communities/dojo?org=acme");
    }

    @Test
    void sessionUrlCarriesTheOrganization() {

        var actual = SessionUrls.sessionUrl("https://axonia.example", "dojo", 42L, "acme");

        assertThat(actual).isEqualTo("https://axonia.example/communities/dojo/sessions/42?org=acme");
    }
}
