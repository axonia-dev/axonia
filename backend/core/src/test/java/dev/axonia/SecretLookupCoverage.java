package dev.axonia;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.core.support.RepositoryFactoryInformation;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * What the running schema and context say about secret lookups: the tables the runtime role may read with no
 * organization in scope, and the table behind every Spring Data repository. Each edition's test holds its own allowlist
 * to these.
 */
public final class SecretLookupCoverage {

    private final NamedParameterJdbcTemplate jdbc;

    private final ListableBeanFactory beans;

    private final RelationalMappingContext mapping;

    public SecretLookupCoverage(NamedParameterJdbcTemplate jdbc, ListableBeanFactory beans,
            RelationalMappingContext mapping) {

        this.jdbc = jdbc;
        this.beans = beans;
        this.mapping = mapping;
    }

    /** Tables with a {@code USING (true)} read policy granted to {@code PUBLIC} or the runtime role. */
    public Set<String> readableWithNoOrganizationInScope() {

        return new TreeSet<>(jdbc.queryForList("""
                SELECT DISTINCT tablename FROM pg_policies
                 WHERE schemaname = 'public' AND cmd IN ('SELECT', 'ALL') AND qual = 'true'
                   AND roles && '{public,axonia_app}'::name[]
                """, Map.of(), String.class));
    }

    /** Every Spring Data repository in the context, by interface name, with the table its entity maps to. */
    public Map<String, String> repositories() {

        var tables = new TreeMap<String, String>();
        for (var name : beans.getBeanNamesForType(RepositoryFactoryInformation.class, false, false)) {
            var information = beans.getBean(name, RepositoryFactoryInformation.class).getRepositoryInformation();
            var entity = mapping.getRequiredPersistentEntity(information.getDomainType());
            tables.put(information.getRepositoryInterface().getName(), entity.getTableName().getReference());
        }
        return tables;
    }
}
