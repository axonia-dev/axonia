package dev.axonia.installation.domain;

import java.time.Instant;

import dev.axonia.communication.api.MailSecurity;
import lombok.Builder;

/** The one row of {@code mail_server_settings}; the password is its sealed envelope. */
@Builder(toBuilder = true)
public record MailServerRow(String host, Integer port, MailSecurity security, String username, String passwordSecret,
        String fromAddress, String fromName, String testedFingerprint, long revision, String lastFailureReason,
        Instant lastFailureAt) {

    public MailValues values(String password) {

        return new MailValues(host, port, security, username, password, fromAddress, fromName);
    }
}
