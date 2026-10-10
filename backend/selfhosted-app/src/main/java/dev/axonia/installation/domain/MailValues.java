package dev.axonia.installation.domain;

import java.util.Optional;
import java.util.function.Predicate;

import dev.axonia.communication.api.MailCredentials;
import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerSettings;
import dev.axonia.communication.api.MailServerState;
import lombok.Builder;

/** One layer of mail server values (stored, the deployment's, a candidate, or the effective ones); null is unset. */
@Builder(toBuilder = true)
public record MailValues(String host, Integer port, MailSecurity security, String username, String password,
        String fromAddress, String fromName) {

    public static final MailValues NONE = MailValues.builder().build();

    Object value(MailField field) {

        return switch (field) {
            case HOST -> host;
            case PORT -> port;
            case SECURITY -> security;
            case USERNAME -> username;
            case PASSWORD -> password;
            case FROM_ADDRESS -> fromAddress;
            case FROM_NAME -> fromName;
        };
    }

    /** These values where they are set, the base's where they are not. */
    public MailValues over(MailValues base) {

        return new MailValues(first(host, base.host), first(port, base.port), first(security, base.security),
                first(username, base.username), first(password, base.password), first(fromAddress, base.fromAddress),
                first(fromName, base.fromName));
    }

    /** These values, with the fields {@code taken} taken from {@code other}. */
    public MailValues with(Predicate<MailField> taken, MailValues other) {

        return new MailValues(pick(taken, MailField.HOST, host, other.host),
                pick(taken, MailField.PORT, port, other.port),
                pick(taken, MailField.SECURITY, security, other.security),
                pick(taken, MailField.USERNAME, username, other.username),
                pick(taken, MailField.PASSWORD, password, other.password),
                pick(taken, MailField.FROM_ADDRESS, fromAddress, other.fromAddress),
                pick(taken, MailField.FROM_NAME, fromName, other.fromName));
    }

    private static <T> T pick(Predicate<MailField> taken, MailField field, T own, T others) {

        return taken.test(field) ? others : own;
    }

    /** Empty without a required value, or with half a credential. */
    public Optional<MailServerSettings> settings() {

        if (host == null || port == null || security == null || fromAddress == null
                || (username == null) != (password == null)) {
            return Optional.empty();
        }
        return Optional.of(MailServerSettings.builder().host(host).port(port).security(security)
                .credentials(Optional.ofNullable(username).map(name -> new MailCredentials(name, password)))
                .fromAddress(fromAddress).fromName(Optional.ofNullable(fromName)).build());
    }

    public MailServerState state() {

        return settings().map(MailServerState::configured).orElseGet(MailServerState::notConfigured);
    }

    @Override
    public String toString() {

        return "MailValues[host=" + host + ", port=" + port + ", security=" + security + ", username=" + username
                + ", password=" + (password == null ? "null" : "<hidden>") + ", fromAddress=" + fromAddress
                + ", fromName=" + fromName + "]";
    }

    private static <T> T first(T value, T fallback) {

        return value != null ? value : fallback;
    }
}
