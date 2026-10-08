package dev.axonia.installation.internal;

import java.time.Instant;

import dev.axonia.communication.MailSecurity;
import lombok.Builder;

/** The one row of {@code mail_server_settings}; the password is its sealed envelope. */
@Builder(toBuilder = true)
record MailServerRow(String host, Integer port, MailSecurity security, String username, String passwordSecret,
        String fromAddress, String fromName, String testedFingerprint, long revision, String lastFailureReason,
        Instant lastFailureAt) {

    MailValues values(String password) {

        return new MailValues(host, port, security, username, password, fromAddress, fromName);
    }
}
