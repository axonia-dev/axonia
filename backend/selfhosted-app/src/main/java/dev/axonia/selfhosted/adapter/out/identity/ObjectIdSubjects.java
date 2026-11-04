package dev.axonia.selfhosted.adapter.out.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.DirectorySubjects;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;

/**
 * An installation that matches accounts on Entra's {@code oid} signs people in with the same key Entra's SCIM
 * provisioning files them under, once its {@code externalId} is mapped from {@code objectId}. Only a GUID is taken for
 * one: Entra's default maps {@code mailNickname} instead, and that must leave a person to be reconciled by address
 * rather than hand them a subject nobody will ever sign in with.
 */
@Component
class ObjectIdSubjects implements DirectorySubjects {

    private static final String OBJECT_ID_CLAIM = "oid";

    private static final Pattern GUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private final boolean matchesOnObjectId;

    ObjectIdSubjects(SelfHostedProperties properties) {
        this.matchesOnObjectId = OBJECT_ID_CLAIM.equals(properties.oidc().subjectClaim());
    }

    @Override
    public Optional<String> subjectOf(String externalId) {

        if (!matchesOnObjectId || externalId == null || !GUID.matcher(externalId).matches()) {
            return Optional.empty();
        }
        return Optional.of(externalId.toLowerCase(Locale.ROOT));
    }
}
