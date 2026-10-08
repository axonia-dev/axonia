package dev.axonia.installation.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.communication.MailSecurity;
import lombok.RequiredArgsConstructor;

/** The one mail server row; every write is conditional on what was read, so two instances never undo each other. */
@Repository
@RequiredArgsConstructor
class MailServerSettingsTable {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * What each revision check reads: the revision, the delivery state, and the identifiers of the keys the password
     * and the tested fingerprint are under (null when none is stored).
     */
    record Watermark(long revision, String lastFailureReason, String passwordKeyId, String fingerprintKeyId) {
    }

    MailServerRow read() {

        return jdbc.queryForObject("""
                SELECT host, port, security, username, password_secret, from_address, from_name, tested_fingerprint,
                       revision, last_failure_reason, last_failure_at
                FROM mail_server_settings
                """, Map.of(), (rs, rowNum) -> row(rs));
    }

    Watermark watermark() {

        return jdbc.queryForObject("""
                SELECT revision, last_failure_reason, split_part(password_secret, ':', 2) AS password_key_id,
                       split_part(tested_fingerprint, ':', 2) AS fingerprint_key_id
                FROM mail_server_settings
                """, Map.of(),
                (rs, rowNum) -> new Watermark(rs.getLong("revision"), rs.getString("last_failure_reason"),
                        rs.getString("password_key_id"), rs.getString("fingerprint_key_id")));
    }

    /**
     * Only on the revision read and the envelope read, so a re-seal never overwrites a save or another instance's
     * re-seal; it moves no revision.
     */
    boolean replacePasswordSecret(long revision, String sealedBefore, String sealedNow) {

        return jdbc.update("""
                UPDATE mail_server_settings SET password_secret = :now
                WHERE revision = :revision AND password_secret = :before
                """, Map.of("revision", revision, "before", sealedBefore, "now", sealedNow)) == 1;
    }

    boolean replaceTestedFingerprint(long revision, String before, String now) {

        return jdbc.update("""
                UPDATE mail_server_settings SET tested_fingerprint = :now
                WHERE revision = :revision AND tested_fingerprint = :before
                """, Map.of("revision", revision, "before", before, "now", now)) == 1;
    }

    /**
     * Keeps when failures began while the reason changes; a delivery state never moves the revision, and is written
     * only on the revision of the settings it is about.
     */
    boolean recordFailure(String reason, Instant at, long revision) {

        return jdbc.update("""
                UPDATE mail_server_settings
                SET last_failure_reason = :reason, last_failure_at = COALESCE(last_failure_at, :at)
                WHERE revision = :revision AND last_failure_reason IS DISTINCT FROM :reason
                """, new MapSqlParameterSource("reason", reason).addValue("at", at.atOffset(ZoneOffset.UTC))
                .addValue("revision", revision)) == 1;
    }

    boolean clearFailure(long revision) {

        return jdbc.update("""
                UPDATE mail_server_settings SET last_failure_reason = NULL, last_failure_at = NULL
                WHERE revision = :revision AND last_failure_reason IS NOT NULL
                """, Map.of("revision", revision)) == 1;
    }

    /** A save or a clear, only on the revision it read; moves the revision and forgets the delivery state. */
    boolean write(MailServerRow next, Instant at) {

        return jdbc.update("""
                UPDATE mail_server_settings
                SET host = :host, port = :port, security = :security, username = :username,
                    password_secret = :passwordSecret, from_address = :fromAddress, from_name = :fromName,
                    tested_fingerprint = :testedFingerprint, revision = revision + 1, last_failure_at = NULL,
                    last_failure_reason = NULL, updated_at = :at
                WHERE revision = :revision
                """,
                new MapSqlParameterSource().addValue("host", next.host(), Types.VARCHAR)
                        .addValue("port", next.port(), Types.INTEGER)
                        .addValue("security", next.security() == null ? null : next.security().name(), Types.VARCHAR)
                        .addValue("username", next.username(), Types.VARCHAR)
                        .addValue("passwordSecret", next.passwordSecret(), Types.VARCHAR)
                        .addValue("fromAddress", next.fromAddress(), Types.VARCHAR)
                        .addValue("fromName", next.fromName(), Types.VARCHAR)
                        .addValue("testedFingerprint", next.testedFingerprint(), Types.VARCHAR)
                        .addValue("revision", next.revision()).addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    private static MailServerRow row(ResultSet rs) throws SQLException {

        var security = rs.getString("security");
        var failedAt = rs.getObject("last_failure_at", OffsetDateTime.class);
        return MailServerRow.builder().host(rs.getString("host")).port(rs.getObject("port", Integer.class))
                .security(security == null ? null : MailSecurity.valueOf(security)).username(rs.getString("username"))
                .passwordSecret(rs.getString("password_secret")).fromAddress(rs.getString("from_address"))
                .fromName(rs.getString("from_name")).testedFingerprint(rs.getString("tested_fingerprint"))
                .revision(rs.getLong("revision")).lastFailureReason(rs.getString("last_failure_reason"))
                .lastFailureAt(failedAt == null ? null : failedAt.toInstant()).build();
    }
}
