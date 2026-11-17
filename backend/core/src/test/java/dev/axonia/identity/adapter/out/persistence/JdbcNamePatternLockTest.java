package dev.axonia.identity.adapter.out.persistence;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lock is held by this test's transaction; a second connection, outside it, asks for the same key without waiting.
 * A save's exclusive hold keeps every name writer out, and a writer's shared hold keeps a save out but not another
 * writer.
 */
class JdbcNamePatternLockTest extends CoreJdbcTestSupport {

    private static final String ORG = "lock-org";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private JdbcNamePatternLock lock;

    @BeforeEach
    void assembleLock() {

        lock = new JdbcNamePatternLock(jdbc);
    }

    @Test
    void aSaveHoldsTheOrganizationExclusively() throws Exception {

        // when
        lock.exclusive(ORG);

        // then
        assertThat(heldModes()).containsExactly("ExclusiveLock");
        assertThat(otherConnectionGets("pg_try_advisory_xact_lock_shared", ORG)).isFalse();
        assertThat(otherConnectionGets("pg_try_advisory_xact_lock_shared", "another-org")).isTrue();
    }

    @Test
    void aNameWriterHoldsTheOrganizationShared() throws Exception {

        // when
        lock.shared(ORG);

        // then
        assertThat(heldModes()).containsExactly("ShareLock");
        assertThat(otherConnectionGets("pg_try_advisory_xact_lock", ORG)).isFalse();
        assertThat(otherConnectionGets("pg_try_advisory_xact_lock_shared", ORG)).isTrue();
    }

    private List<String> heldModes() {

        return jdbc.queryForList("""
                SELECT mode FROM pg_locks
                WHERE locktype = 'advisory' AND pid = pg_backend_pid()
                  AND classid = hashtext(:space)::oid AND objid = hashtext(:org)::oid AND objsubid = 2
                """, Map.of("space", JdbcNamePatternLock.SPACE, "org", ORG), String.class);
    }

    private boolean otherConnectionGets(String tryFunction, String organizationId) throws Exception {

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT " + tryFunction + "(hashtext(?), hashtext(?))")) {
            connection.setAutoCommit(false);
            statement.setString(1, JdbcNamePatternLock.SPACE);
            statement.setString(2, organizationId);
            try (var result = statement.executeQuery()) {
                result.next();
                var granted = result.getBoolean(1);
                connection.rollback();
                return granted;
            }
        }
    }
}
