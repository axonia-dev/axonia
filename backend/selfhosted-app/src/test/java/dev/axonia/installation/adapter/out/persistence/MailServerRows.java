package dev.axonia.installation.adapter.out.persistence;

import java.sql.SQLException;

import dev.axonia.selfhosted.SelfHostedDatabase;

/** The one mail server row, written as the owner; every write bumps the revision so cached copies are re-read. */
public final class MailServerRows {

    private MailServerRows() {
    }

    public static void reset() throws SQLException {

        SelfHostedDatabase.asOwner("""
                UPDATE mail_server_settings SET host = NULL, port = NULL, security = NULL, username = NULL,
                    password_secret = NULL, from_address = NULL, from_name = NULL, tested_fingerprint = NULL,
                    revision = revision + 1, last_failure_at = NULL, last_failure_reason = NULL, updated_at = NULL;
                DELETE FROM installation_settings_change;
                """);
    }

    /** {@code assignments} is SQL, as {@code host = 'localhost', port = 1025}. */
    public static void set(String assignments) throws SQLException {

        SelfHostedDatabase.asOwner("UPDATE mail_server_settings SET " + assignments + ", revision = revision + 1");
    }
}
