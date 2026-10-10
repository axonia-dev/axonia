package dev.axonia.organization.application;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.github.f4b6a3.ulid.UlidCreator;

import dev.axonia.organization.api.OrganizationIdGenerator;

/**
 * ULID-backed id generator: time-sortable, coordination-free, globally unique. Lowercased so the id is a valid Keycloak
 * organization alias and a clean URL path segment.
 */
@Component
public class UlidOrganizationIdGenerator implements OrganizationIdGenerator {

    @Override
    public String newId() {
        return UlidCreator.getUlid().toString().toLowerCase(Locale.ROOT);
    }
}
