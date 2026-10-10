package dev.axonia.shared.adapter.out.persistence;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Startup guard that verifies the runtime database role is subject to Row-Level Security.
 *
 * <p>
 * On {@link ApplicationReadyEvent}, queries {@code pg_roles} through the primary (app) datasource to determine whether
 * the connected role is a superuser or holds {@code BYPASSRLS}. Either privilege silently disables organization
 * isolation — the app must connect as a least-privilege role ({@code axonia_app}) that is subject to all RLS policies.
 *
 * <p>
 * The check is advisory: a query failure never aborts startup. In the normal test suite the datasource connects as the
 * Testcontainers superuser, so the WARN is expected there and does not indicate a regression.
 */
@Component
class RlsRuntimeRoleCheck {

    private static final Logger log = LoggerFactory.getLogger(RlsRuntimeRoleCheck.class);

    private static final String BYPASS_QUERY = "SELECT current_user AS u, (rolsuper OR rolbypassrls) AS bypass FROM pg_roles WHERE rolname = current_user";

    private final JdbcTemplate jdbc;

    RlsRuntimeRoleCheck(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        try {
            boolean bypasses = runtimeRoleBypassesRls(jdbc);
            if (bypasses) {
                log.warn(
                        "RLS DISABLED RISK: runtime DB role '{}' bypasses row-level security"
                                + " (superuser/BYPASSRLS) — organization isolation is NOT enforced."
                                + " The app must connect as a least-privilege non-owner role (axonia_app).",
                        currentUser());
            } else {
                log.info(
                        "RLS guard: runtime DB role '{}' is subject to row-level security — organization isolation enforced.",
                        currentUser());
            }
        } catch (Exception ex) {
            log.warn("RLS guard: could not verify runtime DB role against pg_roles — skipping check.", ex);
        }
    }

    /**
     * Returns {@code true} when the role currently connected to the datasource is a superuser or holds
     * {@code BYPASSRLS}, meaning RLS policies are not enforced for that role.
     *
     * <p>
     * Package-visible so it can be exercised by a focused unit test without standing up a real database.
     */
    boolean runtimeRoleBypassesRls(JdbcTemplate template) {
        Boolean bypass = template.queryForObject(BYPASS_QUERY, (rs, rowNum) -> rs.getBoolean("bypass"));
        return Boolean.TRUE.equals(bypass);
    }

    private String currentUser() {
        try {
            return jdbc.queryForObject("SELECT current_user", String.class);
        } catch (Exception ex) {
            return "<unknown>";
        }
    }
}
