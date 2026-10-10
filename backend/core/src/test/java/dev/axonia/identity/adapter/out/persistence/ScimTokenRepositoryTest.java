package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.shared.api.secret.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;

class ScimTokenRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    ScimTokenRepository repository;

    @Test
    void saveAndFindByTokenHash() {

        // given
        var plaintext = "scim_test-secret-roundtrip";
        var hash = TokenHasher.sha256Hex(plaintext);

        repository
                .save(ScimTokenEntity.builder().organizationId("default").tokenHash(hash).label("test-token").build());

        // when
        var found = repository.findByTokenHash(hash);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getOrganizationId()).isEqualTo("default");
        assertThat(found.get().getTokenHash()).isEqualTo(hash);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void findByTokenHash_missingReturnsEmpty() {

        // then
        assertThat(repository.findByTokenHash("no-such-hash")).isEmpty();
    }

    @Test
    void saveAndReadBackLifecycleColumns() {

        // given
        var hash = TokenHasher.sha256Hex("scim_test-lifecycle-roundtrip");
        var revokedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        var expiresAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        repository.save(ScimTokenEntity.builder().organizationId("default").tokenHash(hash).label("lifecycle-token")
                .revokedAt(revokedAt).expiresAt(expiresAt).build());

        // when
        var found = repository.findByTokenHash(hash);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getRevokedAt()).isEqualTo(revokedAt);
        assertThat(found.get().getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void findByOrganizationIdAndId_scopesToOwningOrganization() {

        // given a token owned by 'default'
        var hash = TokenHasher.sha256Hex("scim_test-org-scoped");
        var saved = repository.save(
                ScimTokenEntity.builder().organizationId("default").tokenHash(hash).label("org-scoped-token").build());

        // then the owning org finds it
        assertThat(repository.findByOrganizationIdAndId("default", saved.getId())).isPresent();

        // and a different org does not — the finder filters by organization_id (RLS read is USING(true))
        assertThat(repository.findByOrganizationIdAndId("acme", saved.getId())).isEmpty();
    }

    @Test
    void toStringDoesNotLeakTokenHash() {

        // given
        var hash = TokenHasher.sha256Hex("scim_test-tostring-secret");
        var entity = ScimTokenEntity.builder().organizationId("default").tokenHash(hash).label("tostring-token")
                .build();

        // then
        assertThat(entity.toString()).doesNotContain(hash);
    }
}
