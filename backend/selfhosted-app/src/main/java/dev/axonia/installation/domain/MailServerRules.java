package dev.axonia.installation.domain;

/**
 * The request formats, as patterns a blank value passes (it is read as unset), and the character rules
 * {@code MailServerSettings} and {@code MailCredentials} hold a host and a line of text to. The patterns read
 * {@code \s} and {@code \p{Cntrl}} as Unicode does, so a request meets the same rule its values meet in delivery.
 */
public final class MailServerRules {

    public static final String HOST = "(?U)\\s*|[^\\s\\p{Cntrl}]+";

    public static final String SECURITY = "\\s*|STARTTLS|TLS|NONE";

    public static final String ONE_LINE = "(?U)\\P{Cntrl}*";

    static final int PASSWORD_LENGTH = 1024;

    private MailServerRules() {
    }

    static boolean isHost(String value) {

        return value.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
    }

    static boolean isOneLine(String value) {

        return value.chars().noneMatch(Character::isISOControl);
    }
}
