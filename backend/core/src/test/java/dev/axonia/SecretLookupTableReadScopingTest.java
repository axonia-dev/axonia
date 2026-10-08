package dev.axonia;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The token and invite tables carry a {@code FOR SELECT USING (true)} policy so the pre-authentication secret lookup
 * can run before the org GUC is set (see the first migration of either chain). RLS therefore provides NO read isolation
 * on these tables: org scoping rests entirely on the explicit organization predicate in each query. This test pins that
 * invariant so a future finder cannot silently widen the read surface. This class holds the open tables to the open
 * allowlist; {@code CloudSecretLookupTableReadScopingTest} holds the Cloud's tables to both.
 */
class SecretLookupTableReadScopingTest extends CoreJdbcTestSupport {

    static final Map<String, Set<String>> ALLOWLISTED_SECRET_LOOKUPS = Map.of(
            "dev.axonia.identity.internal.PersonalAccessTokenRepository", Set.of("findByTokenHash"),
            "dev.axonia.identity.internal.ScimTokenRepository", Set.of("findByTokenHash"),
            "dev.axonia.identity.internal.AgentConnectionTokenRepository", Set.of("findByTokenHash"),
            "dev.axonia.events.internal.CalendarSubscriptionRepository", Set.of("findByTokenHash"));

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private ListableBeanFactory beans;

    @Autowired
    private RelationalMappingContext mapping;

    @Test
    void everyDeclaredFinderIsOrgScopedOrAnAllowlistedSecretLookup() throws ClassNotFoundException {

        assertEveryFinderIsOrgScopedOrAllowlisted(ALLOWLISTED_SECRET_LOOKUPS);
    }

    @Test
    void everyOpenTableAnyoneMayReadHasAnAllowlistedRepository() throws ClassNotFoundException {

        // given
        var covered = new TreeSet<String>();
        for (var repository : ALLOWLISTED_SECRET_LOOKUPS.keySet()) {
            covered.add(tableOf(repository));
        }

        // when
        var readableByAnyone = coverage().readableWithNoOrganizationInScope();

        // then
        assertThat(readableByAnyone)
                .as("open tables the runtime role may read with no organization in scope: "
                        + "allowlist the repository that looks each one up")
                .containsExactlyInAnyOrderElementsOf(covered);
    }

    @Test
    void everyOpenRepositoryOverATableAnyoneMayReadIsAllowlisted() {

        // given
        var readableByAnyone = coverage().readableWithNoOrganizationInScope();

        // when
        var repositories = coverage().repositories();
        var unlisted = repositories.entrySet().stream()
                .filter(repository -> readableByAnyone.contains(repository.getValue())).map(Map.Entry::getKey)
                .filter(name -> !ALLOWLISTED_SECRET_LOOKUPS.containsKey(name)).sorted().toList();

        // then
        assertThat(repositories).as("the scan sees every allowlisted repository")
                .containsKeys(ALLOWLISTED_SECRET_LOOKUPS.keySet().toArray(String[]::new));
        assertThat(unlisted).as("open repositories over a table the runtime role may read with no organization in "
                + "scope: allowlist each one, so its finders are held to the organization predicate").isEmpty();
    }

    static void assertEveryFinderIsOrgScopedOrAllowlisted(Map<String, Set<String>> allowlist)
            throws ClassNotFoundException {

        for (var entry : allowlist.entrySet()) {
            var repository = Class.forName(entry.getKey());
            var finders = Arrays.stream(repository.getDeclaredMethods()).map(Method::getName)
                    .filter(name -> name.startsWith("find")).filter(name -> !entry.getValue().contains(name)).toList();
            assertThat(finders)
                    .as("Non-allowlisted finders on %s must carry an organization predicate because its SELECT "
                            + "policy is USING (true)", repository.getSimpleName())
                    .allSatisfy(name -> assertThat(name).startsWith("findByOrganizationId"));
        }
    }

    private SecretLookupCoverage coverage() {

        return new SecretLookupCoverage(jdbc, beans, mapping);
    }

    /** The table a Spring Data repository's entity maps to. */
    static String tableOf(String repositoryName) throws ClassNotFoundException {

        var repository = Class.forName(repositoryName);
        var crud = (ParameterizedType) repository.getGenericInterfaces()[0];
        return ((Class<?>) crud.getActualTypeArguments()[0]).getAnnotation(Table.class).value();
    }
}
