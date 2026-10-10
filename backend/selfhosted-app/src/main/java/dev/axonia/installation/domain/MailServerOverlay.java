package dev.axonia.installation.domain;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerSettings;

/**
 * The fields the deployment locks, checked once at start against the rules delivery itself holds them to; a malformed
 * variable refuses the start and names it, never its value.
 */
public final class MailServerOverlay {

    private static final Pattern PORT = Pattern.compile("\\d{1,5}");

    private static final String HOST_RULE = "must be a host name with no space or control character.";

    private static final String TEXT_RULE = "must hold no control character.";

    private final MailValues values;

    private MailServerOverlay(MailValues values) {

        this.values = values;
    }

    public static MailServerOverlay from(MailServerVariables properties) {

        return new MailServerOverlay(MailValues.builder()
                .host(checked(MailField.HOST, properties.host(), MailServerRules::isHost, HOST_RULE))
                .port(port(properties.port())).security(security(properties.security()))
                .username(checked(MailField.USERNAME, properties.username(), MailServerRules::isOneLine, TEXT_RULE))
                .password(password(properties.password())).fromAddress(address(properties.fromAddress()))
                .fromName(checked(MailField.FROM_NAME, properties.fromName(), MailServerRules::isOneLine, TEXT_RULE))
                .build());
    }

    public MailValues values() {

        return values;
    }

    public boolean locks(MailField field) {

        return values.value(field) != null;
    }

    /** The variable that sets the field, or null when the field is the stored one. */
    public String lockedBy(MailField field) {

        return locks(field) ? field.variable() : null;
    }

    private static String checked(MailField field, String value, Predicate<String> rule, String ruleText) {

        var stripped = stripped(value);
        if (stripped != null && !rule.test(stripped)) {
            throw refused(field, ruleText);
        }
        return stripped;
    }

    /** Kept as written, spaces included, but on one line: a Secret's trailing newline is not part of a password. */
    private static String password(String value) {

        if (value == null || value.isEmpty()) {
            return null;
        }
        if (!MailServerRules.isOneLine(value)) {
            throw refused(MailField.PASSWORD, "must be one line.");
        }
        return value;
    }

    private static Integer port(String value) {

        var stripped = stripped(value);
        if (stripped == null) {
            return null;
        }
        if (!PORT.matcher(stripped).matches() || Integer.parseInt(stripped) < 1
                || Integer.parseInt(stripped) > 65_535) {
            throw refused(MailField.PORT, "must be a port number from 1 to 65535.");
        }
        return Integer.valueOf(stripped);
    }

    private static MailSecurity security(String value) {

        var stripped = stripped(value);
        if (stripped == null) {
            return null;
        }
        try {
            return MailSecurity.valueOf(stripped.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw refused(MailField.SECURITY, "must be STARTTLS, TLS or NONE.");
        }
    }

    /** The rule delivery itself holds a from address to, so a value it would refuse never reaches it. */
    private static String address(String value) {

        var stripped = stripped(value);
        if (stripped != null && !MailServerSettings.isMailAddress(stripped)) {
            throw refused(MailField.FROM_ADDRESS, "must be a mail address.");
        }
        return stripped;
    }

    private static String stripped(String value) {

        return value == null || value.isBlank() ? null : value.strip();
    }

    private static IllegalStateException refused(MailField field, String rule) {

        return new IllegalStateException(field.variable() + " " + rule);
    }
}
