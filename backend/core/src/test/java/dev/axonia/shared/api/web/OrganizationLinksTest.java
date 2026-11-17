package dev.axonia.shared.api.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationLinksTest {

    @Test
    void appendsTheOrganizationAsTheFirstQuery() {

        assertThat(OrganizationLinks.withOrganization("https://a/x", "acme")).isEqualTo("https://a/x?org=acme");
    }

    @Test
    void appendsAfterAnExistingQueryAndEncodes() {

        var actual = OrganizationLinks.withOrganization("https://a/x?term=y", "org 1");

        assertThat(actual).isEqualTo("https://a/x?term=y&org=org+1");
    }
}
