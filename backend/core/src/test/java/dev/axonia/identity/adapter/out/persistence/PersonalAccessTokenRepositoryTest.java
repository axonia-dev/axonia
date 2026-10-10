package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.shared.api.secret.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalAccessTokenRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    PersonalAccessTokenRepository repository;

    @Test
    void saveAndFindByTokenHash() {
        var hash = TokenHasher.sha256Hex("axp_roundtrip");
        repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1").tokenHash(hash)
                .name("Claude desktop").scopes("read,library:contribute").build());

        var found = repository.findByTokenHash(hash);

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("u-1");
        assertThat(found.get().getScopes()).isEqualTo("read,library:contribute");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void findByOwner_scopesToOrgAndUser_newestFirst() {
        repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1")
                .tokenHash(TokenHasher.sha256Hex("axp_a")).name("a").build());
        repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1")
                .tokenHash(TokenHasher.sha256Hex("axp_b")).name("b").build());
        repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-2")
                .tokenHash(TokenHasher.sha256Hex("axp_other")).name("other").build());

        var mine = repository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc("default", "u-1");

        assertThat(mine).hasSize(2).extracting(PersonalAccessTokenEntity::getUserId).containsOnly("u-1");
    }

    @Test
    void findByOrganizationIdAndUserIdAndId_isOwnerScoped() {
        var saved = repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1")
                .tokenHash(TokenHasher.sha256Hex("axp_owned")).name("owned").build());

        assertThat(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", saved.getId())).isPresent();
        assertThat(repository.findByOrganizationIdAndUserIdAndId("default", "u-2", saved.getId())).isEmpty();
    }

    @Test
    void touchLastUsed_advancesTimestamp() {
        var saved = repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1")
                .tokenHash(TokenHasher.sha256Hex("axp_touch")).name("touch").build());
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        repository.touchLastUsed("default", saved.getId(), now, now.minusSeconds(60));

        assertThat(repository.findByTokenHash(TokenHasher.sha256Hex("axp_touch")).orElseThrow().getLastUsedAt())
                .isEqualTo(now);
    }

    @Test
    void touchLastUsed_writesAtMostOncePerMinute() {

        // given
        var saved = repository.save(PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1")
                .tokenHash(TokenHasher.sha256Hex("axp_throttle")).name("throttle").build());
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.touchLastUsed("default", saved.getId(), now, now.minusSeconds(60));

        // when
        var soon = now.plusSeconds(59);
        repository.touchLastUsed("default", saved.getId(), soon, soon.minusSeconds(60));
        var skipped = repository.findById(saved.getId()).orElseThrow().getLastUsedAt();
        var later = now.plusSeconds(61);
        repository.touchLastUsed("default", saved.getId(), later, later.minusSeconds(60));

        // then
        assertThat(skipped).isEqualTo(now);
        assertThat(repository.findById(saved.getId()).orElseThrow().getLastUsedAt()).isEqualTo(later);
    }

    @Test
    void toStringDoesNotLeakTokenHash() {
        var hash = TokenHasher.sha256Hex("axp_secret");
        var entity = PersonalAccessTokenEntity.builder().organizationId("default").userId("u-1").tokenHash(hash)
                .name("x").build();

        assertThat(entity.toString()).doesNotContain(hash);
    }
}
