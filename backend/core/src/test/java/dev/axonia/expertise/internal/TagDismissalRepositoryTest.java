package dev.axonia.expertise.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.expertise.internal.TagDismissalRepository.TagPair;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TagDismissalRepositoryImpl}. Runs as the Testcontainers superuser (table owner), which
 * bypasses RLS, matching every {@code JdbcTestSupport} integration test in this codebase. The plain {@code @Repository}
 * bean is imported via {@link ExpertiseJdbcTestSupport}.
 */
class TagDismissalRepositoryTest extends ExpertiseJdbcTestSupport {

    @Autowired
    private TagDismissalRepositoryImpl repo;

    private static final String ORG = "default";
    private static final long COMMUNITY = 1L;
    private static final long OTHER_COMMUNITY = 2L;

    @Test
    void addDismissal_thenDismissedPairs_returnsPair() {

        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).containsExactly(new TagPair("spring", "spring-boot"));
    }

    @Test
    void addDismissal_isIdempotent() {

        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot");
        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot"); // second call - no exception

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).containsExactly(new TagPair("spring", "spring-boot"));
    }

    @Test
    void aParkedPairIsParkedForOneCommunityOnly() {

        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).containsExactly(new TagPair("spring", "spring-boot"));
        assertThat(repo.dismissedPairs(ORG, OTHER_COMMUNITY)).isEmpty();
    }

    @Test
    void removeDismissal_deletesPair() {

        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot");
        repo.removeDismissal(ORG, COMMUNITY, "spring", "spring-boot");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).isEmpty();
    }

    @Test
    void removeDismissal_isIdempotent() {

        repo.removeDismissal(ORG, COMMUNITY, "spring", "spring-boot"); // absent - no exception

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).isEmpty();
    }

    @Test
    void removeDismissal_onlyRemovesTargetPair() {

        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot");
        repo.addDismissal(ORG, COMMUNITY, "react", "react-native");

        repo.removeDismissal(ORG, COMMUNITY, "spring", "spring-boot");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).containsExactly(new TagPair("react", "react-native"));
    }

    @Test
    void removeDismissalsNaming_dropsEveryPairOnEitherSideOfTheTag() {

        repo.addDismissal(ORG, COMMUNITY, "misc", "misk");
        repo.addDismissal(ORG, COMMUNITY, "junk", "misc");
        repo.addDismissal(ORG, COMMUNITY, "react", "react-native");

        repo.removeDismissalsNaming(ORG, COMMUNITY, "misc");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).containsExactly(new TagPair("react", "react-native"));
    }

    @Test
    void removeDismissalsNaming_leavesTheOtherCommunitysVerdictAlone() {

        repo.addDismissal(ORG, COMMUNITY, "misc", "misk");
        repo.addDismissal(ORG, OTHER_COMMUNITY, "misc", "misk");

        repo.removeDismissalsNaming(ORG, COMMUNITY, "misc");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).isEmpty();
        assertThat(repo.dismissedPairs(ORG, OTHER_COMMUNITY)).containsExactly(new TagPair("misc", "misk"));
    }

    @Test
    void unparkingLeavesTheOtherCommunitysVerdictAlone() {

        repo.addDismissal(ORG, COMMUNITY, "spring", "spring-boot");
        repo.addDismissal(ORG, OTHER_COMMUNITY, "spring", "spring-boot");

        repo.removeDismissal(ORG, COMMUNITY, "spring", "spring-boot");

        assertThat(repo.dismissedPairs(ORG, COMMUNITY)).isEmpty();
        assertThat(repo.dismissedPairs(ORG, OTHER_COMMUNITY)).containsExactly(new TagPair("spring", "spring-boot"));
    }
}
