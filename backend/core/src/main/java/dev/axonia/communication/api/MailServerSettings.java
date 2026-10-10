package dev.axonia.communication.api;

import java.util.Objects;
import java.util.Optional;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import dev.axonia.shared.api.secret.TokenHasher;
import lombok.Builder;

/**
 * One mail server as delivery uses it, valid by construction: one host, a port in range, one bare from address. An
 * absent credential or from name is an empty {@link Optional}, never null, whatever the builder was given.
 */
@Builder
public record MailServerSettings(String host, int port, MailSecurity security, Optional<MailCredentials> credentials,
        String fromAddress, Optional<String> fromName) {

    private static final String FINGERPRINT_VERSION = "mail-server-settings/v1";
    private static final String ABSENT = "~;";
    private static final int HIGHEST_PORT = 65_535;

    public MailServerSettings {
        if (host == null || host.isBlank()
                || host.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("host must be one host name or address");
        }
        if (port < 1 || port > HIGHEST_PORT) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Objects.requireNonNull(security, "security");
        credentials = Objects.requireNonNullElse(credentials, Optional.empty());
        if (!isMailAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress must be one bare mail address");
        }
        fromName = Objects.requireNonNullElse(fromName, Optional.<String>empty()).map(String::strip)
                .filter(name -> !name.isEmpty());
        if (fromName.filter(MailServerSettings::hasControlCharacter).isPresent()) {
            throw new IllegalArgumentException("fromName must be one line");
        }
    }

    /** One address, no display name, nothing around it: what a from address and a test recipient must be. */
    public static boolean isMailAddress(String candidate) {

        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        try {
            var parsed = new InternetAddress(candidate, true);
            return parsed.getPersonal() == null && candidate.equals(parsed.getAddress());
        } catch (AddressException e) {
            return false;
        }
    }

    /**
     * SHA-256 over every field, the password included, each written as its length, a colon, itself and a semicolon, so
     * no two different settings share an input. Stable across versions only while {@code FINGERPRINT_VERSION} is.
     */
    public String fingerprint() {

        var canonical = new StringBuilder();
        field(canonical, Optional.of(FINGERPRINT_VERSION));
        field(canonical, Optional.of(host));
        field(canonical, Optional.of(Integer.toString(port)));
        field(canonical, Optional.of(security.name()));
        field(canonical, credentials.map(MailCredentials::username));
        field(canonical, credentials.map(MailCredentials::password));
        field(canonical, Optional.of(fromAddress));
        field(canonical, fromName);
        return TokenHasher.sha256Hex(canonical.toString());
    }

    static boolean hasControlCharacter(String value) {

        return value.chars().anyMatch(Character::isISOControl);
    }

    private static void field(StringBuilder canonical, Optional<String> value) {

        canonical.append(value.map(present -> present.length() + ":" + present + ";").orElse(ABSENT));
    }
}
