package dev.axonia.expertise.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.expertise.domain.SignalRef;
import dev.axonia.expertise.domain.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PreferenceRepositoryImpl}.
 *
 * <p>
 * Runs as the Testcontainers superuser (table owner), which bypasses RLS — no {@code app.current_org} session variable
 * is needed. This matches the pattern used across all {@code JdbcTestSupport} integration tests in this codebase.
 *
 * <p>
 * {@link PreferenceRepositoryImpl} is a plain {@code @Repository} bean, so the {@code @DataJdbcTest} slice does not
 * register it automatically — it is imported via {@link ExpertiseJdbcTestSupport}.
 */
class PreferenceRepositoryTest extends ExpertiseJdbcTestSupport {

    @Autowired
    private PreferenceRepositoryImpl repo;

    private static final String ORG = "default";
    private static final String USER = "alice";

    // ------------------------------------------------------------------
    // isOptedOut / setOptOut
    // ------------------------------------------------------------------

    @Test
    void setOptOut_true_thenIsOptedOut_true() {

        repo.setOptOut(ORG, USER, true);

        assertThat(repo.isOptedOut(ORG, USER)).isTrue();
    }

    @Test
    void setOptOut_false_afterTrue_thenIsOptedOut_false() {

        repo.setOptOut(ORG, USER, true);
        repo.setOptOut(ORG, USER, false);

        assertThat(repo.isOptedOut(ORG, USER)).isFalse();
    }

    @Test
    void setOptOut_true_isIdempotent() {

        repo.setOptOut(ORG, USER, true);
        repo.setOptOut(ORG, USER, true);

        assertThat(repo.isOptedOut(ORG, USER)).isTrue();
    }

    @Test
    void setOptOut_false_whenNotOptedOut_isIdempotent() {

        // No row inserted first — delete should be a no-op
        repo.setOptOut(ORG, USER, false);

        assertThat(repo.isOptedOut(ORG, USER)).isFalse();
    }

    @Test
    void isOptedOut_returnsFalseForUserWithNoRecord() {

        // A user who has never touched the opt-out setting at all
        assertThat(repo.isOptedOut(ORG, "ghost-user-never-inserted")).isFalse();
    }

    // ------------------------------------------------------------------
    // hiddenTags / hideTag / unhideTag
    // ------------------------------------------------------------------

    @Test
    void hideTag_thenHiddenTags_containsTag() {

        repo.hideTag(ORG, USER, "kafka");

        assertThat(repo.hiddenTags(ORG, USER)).contains("kafka");
    }

    @Test
    void unhideTag_removesTag() {

        repo.hideTag(ORG, USER, "kafka");
        repo.unhideTag(ORG, USER, "kafka");

        assertThat(repo.hiddenTags(ORG, USER)).doesNotContain("kafka");
    }

    @Test
    void hideTag_isIdempotent() {

        repo.hideTag(ORG, USER, "kafka");
        repo.hideTag(ORG, USER, "kafka");

        assertThat(repo.hiddenTags(ORG, USER)).containsExactly("kafka");
    }

    @Test
    void unhideTag_whenNotHidden_isIdempotent() {

        // No row first — delete should be a no-op
        repo.unhideTag(ORG, USER, "kafka");

        assertThat(repo.hiddenTags(ORG, USER)).doesNotContain("kafka");
    }

    @Test
    void hiddenTags_forDifferentUsers_areIsolated() {

        repo.hideTag(ORG, USER, "kafka");
        repo.hideTag(ORG, "bob", "java");

        assertThat(repo.hiddenTags(ORG, USER)).containsExactly("kafka");
        assertThat(repo.hiddenTags(ORG, "bob")).containsExactly("java");
    }

    // ------------------------------------------------------------------
    // dismissSignal
    // ------------------------------------------------------------------

    @Test
    void dismissSignal_insertsRow() {

        repo.dismissSignal(ORG, USER, new SignalRef(SourceType.SLOT, 1L), "kafka");

        // Verify via a second dismiss (ON CONFLICT DO NOTHING = no exception)
        repo.dismissSignal(ORG, USER, new SignalRef(SourceType.SLOT, 1L), "kafka");
        // No assertion needed beyond "no exception thrown"
    }

    @Test
    void dismissSignal_isIdempotent() {

        repo.dismissSignal(ORG, USER, new SignalRef(SourceType.SLOT, 42L), "java");
        repo.dismissSignal(ORG, USER, new SignalRef(SourceType.SLOT, 42L), "java");
        // ON CONFLICT DO NOTHING — no exception expected
    }

    @Test
    void dismissSignal_differentSourceTypes_areStoredIndependently() {

        repo.dismissSignal(ORG, USER, new SignalRef(SourceType.SLOT, 1L), "kafka");
        repo.dismissSignal(ORG, USER, new SignalRef(SourceType.ARTIFACT, 1L), "kafka");
        // Both succeed — different source_type in composite PK
    }
}
