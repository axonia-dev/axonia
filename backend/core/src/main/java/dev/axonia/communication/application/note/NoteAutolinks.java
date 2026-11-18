package dev.axonia.communication.application.note;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

/**
 * A bare url ends where the editor's autolinking ends it: trailing punctuation, emphasis marks and an entity-like
 * {@code &name;} stay text, while a quote followed by more of the url stays in it.
 */
final class NoteAutolinks extends AbstractVisitor {

    private static final String SCHEME_END = "://";
    private static final String TRAIL = "!\"')*,.:;?_~";

    private NoteAutolinks() {
    }

    static void align(Node document) {

        document.accept(new NoteAutolinks());
    }

    @Override
    public void visit(Link link) {

        NoteInlines.foundText(link).filter(text -> text.getLiteral().equals(link.getDestination()))
                .ifPresent(text -> realign(link, text));
    }

    private static void realign(Link link, Text text) {

        var url = link.getDestination();
        var next = link.getNext() instanceof Text following ? following : null;
        var after = next == null ? "" : next.getLiteral();
        var candidate = url + after.substring(0, wordEnd(after));
        var end = urlEnd(candidate, url.indexOf(SCHEME_END) + SCHEME_END.length());
        if (end == url.length()) {
            return;
        }
        link.setDestination(candidate.substring(0, end));
        text.setLiteral(candidate.substring(0, end));
        var rest = candidate.substring(end) + after.substring(candidate.length() - url.length());
        if (next == null) {
            link.insertAfter(new Text(rest));
        } else {
            next.setLiteral(rest);
        }
    }

    private static int wordEnd(String text) {

        var end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != '<') {
            end++;
        }
        return end;
    }

    private static int urlEnd(String url, int from) {

        var open = 0;
        var close = 0;
        for (var i = from; i < url.length(); i++) {
            var c = url.charAt(i);
            if (c == '(') {
                open++;
            } else if (c == ')' && close < open) {
                close++;
            } else if (isTrailing(url, i)) {
                return i;
            } else if (c == ')') {
                close++;
            }
        }
        return url.length();
    }

    private static boolean isTrailing(String url, int from) {

        var i = from;
        while (i < url.length()) {
            var c = url.charAt(i);
            if (TRAIL.indexOf(c) >= 0) {
                i++;
            } else if (c == '&') {
                i = entityEnd(url, i + 1);
                if (i < 0) {
                    return false;
                }
            } else if (c == ']') {
                i++;
                if (i == url.length() || url.charAt(i) == '(' || url.charAt(i) == '[') {
                    return true;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static int entityEnd(String url, int from) {

        var i = from;
        while (i < url.length() && isAsciiLetter(url.charAt(i))) {
            i++;
        }
        return i > from && i < url.length() && url.charAt(i) == ';' ? i + 1 : -1;
    }

    private static boolean isAsciiLetter(char c) {

        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }
}
