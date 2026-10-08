package dev.axonia.installation.internal;

import java.util.List;
import java.util.function.Predicate;

import dev.axonia.communication.MailServerSettings;

/**
 * The rules a test or a save holds a request's values to, field by field and in the order of the form: the ones
 * delivery itself holds them to, so a request never meets them as an exception there, and which value is missing.
 */
final class MailValueChecks {

    private static final String REQUIRED = "required.";

    private static final String ONE_LINE = "must hold no control character.";

    private static final List<Rule> FORMATS = List.of(
            new Rule(MailField.HOST, MailServerRules::isHost,
                    "must be a host name with no space or control character."),
            new Rule(MailField.USERNAME, MailServerRules::isOneLine, ONE_LINE),
            new Rule(MailField.FROM_ADDRESS, MailServerSettings::isMailAddress, "must be one bare mail address."),
            new Rule(MailField.FROM_NAME, MailServerRules::isOneLine, ONE_LINE));

    private static final List<MailField> REQUIRED_FIELDS = List.of(MailField.HOST, MailField.PORT, MailField.SECURITY,
            MailField.FROM_ADDRESS);

    private static final Rule PASSWORD = new Rule(MailField.PASSWORD,
            password -> password.length() <= MailServerRules.PASSWORD_LENGTH && MailServerRules.isOneLine(password),
            "must be one line of at most 1024 characters.");

    private MailValueChecks() {
    }

    private record Rule(MailField field, Predicate<String> holds, String text) {

        boolean refuses(Object value) {

            return value instanceof String string && !holds.test(string);
        }
    }

    /** Checked here, not by a bean constraint, so a refused password never reaches a validation message. */
    static void checkPassword(String password) {

        if (PASSWORD.refuses(password)) {
            throw invalid(MailField.PASSWORD, PASSWORD.text());
        }
    }

    static void checkFormats(MailValues effective) {

        FORMATS.stream().filter(rule -> rule.refuses(effective.value(rule.field()))).findFirst().ifPresent(rule -> {
            throw invalid(rule.field(), rule.text());
        });
    }

    /** The first value the form misses, for values {@link MailValues#settings()} found incomplete. */
    static MailServerFieldException missing(MailValues effective) {

        return REQUIRED_FIELDS.stream().filter(field -> effective.value(field) == null).findFirst()
                .map(field -> invalid(field, REQUIRED))
                .orElseGet(() -> effective.username() == null
                        ? invalid(MailField.USERNAME, "required with a password.")
                        : invalid(MailField.PASSWORD, "required with a username."));
    }

    static MailServerFieldException invalid(MailField field, String rule) {

        return new MailServerFieldException("values." + field.apiName(), rule);
    }
}
