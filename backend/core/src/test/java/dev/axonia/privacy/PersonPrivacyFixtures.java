package dev.axonia.privacy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The person both {@code PersonPrivacyRlsTest} and {@code CloudPersonPrivacyRlsTest} sign in as, and the package
 * reading both make of an export. Shared by composition, since a Cloud test cannot extend an open full-context one.
 */
final class PersonPrivacyFixtures {

    static final String SUBJECT = "person-privacy-nomad";
    static final String EMAIL = "nomad@person-privacy.test";
    static final String NAME = "Nomad Nobody";
    static final String ORGANIZATION = "person-privacy-org";
    static final String ORGANIZATION_PROFILE_NAME = "Tenant Persona";
    static final String COMMUNITY_SLUG = "person-privacy-path";

    static final String ONBOARDING_PATH = "INSERT INTO onboarding_path (subject, variant, community_slug, "
            + "anchor_organization_id, anchor_community_id, created_at, updated_at) VALUES ('" + SUBJECT
            + "', 'MEMBER', '" + COMMUNITY_SLUG + "', '" + ORGANIZATION + "', 1, now(), now())";

    static final String TENANT_PROFILE = "INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, "
            + "arrival, created_at, updated_at, version) VALUES ('" + ORGANIZATION + "', '" + SUBJECT + "', '"
            + ORGANIZATION_PROFILE_NAME + "', 'scim-person-privacy', 'SYNCED', now(), now(), 0)";

    private PersonPrivacyFixtures() {
    }

    /** A token that names the person and no Organization. */
    static RequestPostProcessor signedInWithNoOrganization() {

        return jwt().jwt(token -> token.subject(SUBJECT).claim("email", EMAIL).claim("name", NAME));
    }

    /** Each entry of an export, by its path inside the archive. */
    static Map<String, String> unzip(byte[] zip) throws IOException {

        var entries = new LinkedHashMap<String, String>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    /** The archive path of the entry with this file name, whatever folder the export puts it in. */
    static String entryNamed(Map<String, String> entries, String fileName) {

        return entries.keySet().stream().filter(name -> name.endsWith("/" + fileName)).findFirst().orElseThrow();
    }
}
