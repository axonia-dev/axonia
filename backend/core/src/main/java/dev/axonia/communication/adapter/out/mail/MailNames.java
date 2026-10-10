package dev.axonia.communication.adapter.out.mail;

/**
 * Blanks every address, host and domain name out of a mail server's message, so only the server's own words are read:
 * anything in angle brackets (to the end when nothing closes it), a whitespace-free token holding an {@code @} with
 * something on both sides of it, and a dotted name such as {@code tls.acme.com} or {@code 5.7.1}. Each is replaced by
 * one space.
 *
 * <p>
 * A scan rather than a pattern, and one pass whatever the input: a pattern for a dotted name nests one quantifier
 * inside another, which is the shape that backtracks without end on a long enough reply. At every position the three
 * are tried in that order, as one alternation would; a token that holds no address at one position holds none at any
 * later one, and a dotted name never starts inside a run of name characters, so neither is looked for again there.
 */
final class MailNames {

    private static final int NONE = -1;
    private static final String NAME_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-";
    private static final String WHITESPACE = " \t\n\u000B\f\r";

    private final String text;
    private int addressAbsentBefore;

    private MailNames(String text) {

        this.text = text;
    }

    static String blankedIn(String text) {

        return new MailNames(text).blanked();
    }

    private String blanked() {

        var blanked = new StringBuilder(text.length());
        var position = 0;
        while (position < text.length()) {
            var end = nameEnd(position);
            if (end == NONE) {
                blanked.append(text.charAt(position));
                position++;
            } else {
                blanked.append(' ');
                position = end;
            }
        }
        return blanked.toString();
    }

    private int nameEnd(int position) {

        if (text.charAt(position) == '<') {
            var close = text.indexOf('>', position + 1);
            return close == NONE ? text.length() : close + 1;
        }
        var address = addressEnd(position);
        if (address != NONE) {
            return address;
        }
        return startsAName(position) ? dottedNameEnd(position) : NONE;
    }

    private int addressEnd(int position) {

        if (position < addressAbsentBefore) {
            return NONE;
        }
        var end = position;
        var address = false;
        while (end < text.length() && WHITESPACE.indexOf(text.charAt(end)) < 0) {
            address |= end > position + 1 && text.charAt(end - 1) == '@';
            end++;
        }
        if (address) {
            return end;
        }
        addressAbsentBefore = end;
        return NONE;
    }

    private boolean startsAName(int position) {

        return isNameCharacter(text.charAt(position)) && (position == 0 || !isNameCharacter(text.charAt(position - 1)));
    }

    private int dottedNameEnd(int position) {

        var end = runEnd(position);
        var dotted = NONE;
        while (end + 1 < text.length() && text.charAt(end) == '.' && isNameCharacter(text.charAt(end + 1))) {
            end = runEnd(end + 1);
            dotted = end;
        }
        return dotted;
    }

    private int runEnd(int from) {

        var end = from;
        while (end < text.length() && isNameCharacter(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isNameCharacter(char character) {

        return NAME_CHARACTERS.indexOf(character) >= 0;
    }
}
