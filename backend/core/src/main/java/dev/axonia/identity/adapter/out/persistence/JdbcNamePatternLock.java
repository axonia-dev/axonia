package dev.axonia.identity.adapter.out.persistence;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.NamePatternLock;
import lombok.RequiredArgsConstructor;

/**
 * A transaction-scoped Postgres advisory lock keyed on the Organization, released at commit or rollback. Mandatory
 * propagation: a lock taken outside the writer's transaction would be released before the write it guards.
 */
@Component
@RequiredArgsConstructor
class JdbcNamePatternLock implements NamePatternLock {

    static final String SPACE = "identity_name_pattern";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void shared(String organizationId) {

        take("pg_advisory_xact_lock_shared", organizationId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void exclusive(String organizationId) {

        take("pg_advisory_xact_lock", organizationId);
    }

    private void take(String function, String organizationId) {

        jdbc.queryForList("SELECT " + function + "(hashtext(:space), hashtext(:org))",
                Map.of("space", SPACE, "org", organizationId));
    }
}
