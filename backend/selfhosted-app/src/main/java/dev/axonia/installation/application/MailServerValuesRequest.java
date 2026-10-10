package dev.axonia.installation.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import dev.axonia.communication.api.MailSecurity;
import dev.axonia.installation.domain.MailServerRules;
import dev.axonia.installation.domain.MailValues;

/** The password carries no bean constraint, so a refused one never reaches a validation message or its log line. */
public record MailServerValuesRequest(@Size(max = 253) @Pattern(regexp = MailServerRules.HOST) String host,
        @Min(1) @Max(65_535) Integer port, @Pattern(regexp = MailServerRules.SECURITY) String security,
        @Size(max = 255) @Pattern(regexp = MailServerRules.ONE_LINE) String username, String password,
        @Size(max = 254) @Pattern(regexp = MailServerRules.ONE_LINE) String fromAddress,
        @Size(max = 120) @Pattern(regexp = MailServerRules.ONE_LINE) String fromName) {

    /** Blank text is unset; a password is kept as typed. */
    public MailValues values() {

        var mode = text(security);
        return new MailValues(text(host), port, mode == null ? null : MailSecurity.valueOf(mode), text(username),
                password == null || password.isEmpty() ? null : password, text(fromAddress), text(fromName));
    }

    @Override
    public String toString() {

        return "MailServerValuesRequest[host=" + host + ", port=" + port + ", password="
                + (password == null ? "null" : "<hidden>") + "]";
    }

    private static String text(String value) {

        return value == null || value.isBlank() ? null : value.strip();
    }
}
