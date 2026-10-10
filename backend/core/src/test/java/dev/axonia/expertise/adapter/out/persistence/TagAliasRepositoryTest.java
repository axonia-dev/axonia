package dev.axonia.expertise.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Integration tests for {@link TagAliasRepositoryImpl}.
 *
 * <p>
 * Runs as the Testcontainers superuser (table owner), which bypasses RLS: no {@code app.current_org} session variable
 * is needed. This matches the pattern used across all {@code JdbcTestSupport} integration tests in this codebase.
 *
 * <p>
 * {@link TagAliasRepositoryImpl} is a plain {@code @Repository} bean, so the {@code @DataJdbcTest} slice does not
 * register it automatically: it is imported via {@link ExpertiseJdbcTestSupport}.
 */
class TagAliasRepositoryTest extends ExpertiseJdbcTestSupport {

    @Autowired
    private TagAliasRepositoryImpl repo;

    private static final String ORG = "default";
    private static final long COMMUNITY = 1L;
    private static final long OTHER_COMMUNITY = 2L;

    // -----------------------------------------------------------------------
    // addAlias + aliasMap
    // -----------------------------------------------------------------------

    @Test
    void addAlias_thenAliasMap_returnsEntry() {

        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).containsEntry("k8s", "kubernetes");
    }

    @Test
    void addAlias_calledTwice_updatesCanonical() {

        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        repo.addAlias(ORG, COMMUNITY, "k8s", "k8s-updated");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).containsEntry("k8s", "k8s-updated");
    }

    @Test
    void addAlias_multipleEntries_allReturnedByAliasMap() {

        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        repo.addAlias(ORG, COMMUNITY, "spring-boot", "spring");

        var map = repo.aliasMap(ORG, COMMUNITY);
        assertThat(map).containsEntry("k8s", "kubernetes");
        assertThat(map).containsEntry("spring-boot", "spring");
    }

    @Test
    void aliasesAreScopedToTheirCommunity() {

        repo.addAlias(ORG, COMMUNITY, "kafak", "kafka");
        repo.addAlias(ORG, OTHER_COMMUNITY, "kafka", "apache-kafka");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).containsExactly(entry("kafak", "kafka"));
        assertThat(repo.aliasMap(ORG, OTHER_COMMUNITY)).containsExactly(entry("kafka", "apache-kafka"));
    }

    // -----------------------------------------------------------------------
    // aliasMaps - one map per requested community, for the self-view
    // -----------------------------------------------------------------------

    @Test
    void aliasMapsReturnsOneMapPerRequestedCommunity() {

        repo.addAlias(ORG, COMMUNITY, "kafak", "kafka");

        var maps = repo.aliasMaps(ORG, List.of(COMMUNITY, OTHER_COMMUNITY));

        assertThat(maps.get(COMMUNITY)).containsExactly(entry("kafak", "kafka"));
        assertThat(maps.get(OTHER_COMMUNITY)).isEmpty();
    }

    @Test
    void aliasMapsOfNoCommunitiesIsEmpty() {

        repo.addAlias(ORG, COMMUNITY, "kafak", "kafka");

        assertThat(repo.aliasMaps(ORG, List.of())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // removeAlias
    // -----------------------------------------------------------------------

    @Test
    void removeAlias_deletesEntry() {

        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        repo.removeAlias(ORG, COMMUNITY, "k8s");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).doesNotContainKey("k8s");
    }

    @Test
    void removeAlias_isIdempotent() {

        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        repo.removeAlias(ORG, COMMUNITY, "k8s");
        repo.removeAlias(ORG, COMMUNITY, "k8s"); // second call, no exception

        assertThat(repo.aliasMap(ORG, COMMUNITY)).doesNotContainKey("k8s");
    }

    @Test
    void removeAlias_onlyRemovesTargetVariant() {

        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        repo.addAlias(ORG, COMMUNITY, "kube", "kubernetes");

        repo.removeAlias(ORG, COMMUNITY, "k8s");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).doesNotContainKey("k8s");
        assertThat(repo.aliasMap(ORG, COMMUNITY)).containsEntry("kube", "kubernetes");
    }

    @Test
    void removingAnAliasLeavesTheOtherCommunitysAliasAlone() {

        repo.addAlias(ORG, COMMUNITY, "kafak", "kafka");
        repo.addAlias(ORG, OTHER_COMMUNITY, "kafak", "kafka");

        repo.removeAlias(ORG, COMMUNITY, "kafak");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).isEmpty();
        assertThat(repo.aliasMap(ORG, OTHER_COMMUNITY)).containsExactly(entry("kafak", "kafka"));
    }

    // -----------------------------------------------------------------------
    // removeAliasesNaming - a retired name takes its vocabulary rows with it
    // -----------------------------------------------------------------------

    @Test
    void removeAliasesNaming_clearsTheRowsOnBothSidesOfTheName() {

        repo.addAlias(ORG, COMMUNITY, "kafak", "kafka");
        repo.addAlias(ORG, COMMUNITY, "kafka", "apache-kafka");
        repo.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");

        repo.removeAliasesNaming(ORG, COMMUNITY, "kafka");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).containsExactly(entry("k8s", "kubernetes"));
    }

    @Test
    void removeAliasesNaming_leavesTheOtherCommunitysRowsAlone() {

        repo.addAlias(ORG, COMMUNITY, "kafak", "kafka");
        repo.addAlias(ORG, OTHER_COMMUNITY, "kafak", "kafka");

        repo.removeAliasesNaming(ORG, COMMUNITY, "kafka");

        assertThat(repo.aliasMap(ORG, COMMUNITY)).isEmpty();
        assertThat(repo.aliasMap(ORG, OTHER_COMMUNITY)).containsExactly(entry("kafak", "kafka"));
    }
}
