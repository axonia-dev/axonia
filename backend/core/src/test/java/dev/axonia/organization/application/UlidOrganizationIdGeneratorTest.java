package dev.axonia.organization.application;

import org.junit.jupiter.api.Test;

import dev.axonia.organization.api.OrganizationIdGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class UlidOrganizationIdGeneratorTest {

    private final OrganizationIdGenerator sut = new UlidOrganizationIdGenerator();

    @Test
    void newId_returnsLowercase26CharCrockfordBase32() {
        var id = sut.newId();
        assertThat(id).hasSize(26).matches("[0-9a-hjkmnp-tv-z]{26}");
    }

    @Test
    void newId_isUniquePerCall() {
        assertThat(sut.newId()).isNotEqualTo(sut.newId());
    }
}
