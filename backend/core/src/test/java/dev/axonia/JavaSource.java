package dev.axonia;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java source text as the compiler reads it, for the guards that scan test code: comments are not code. */
public final class JavaSource {

    private static final Pattern COMMENT_OR_LITERAL = Pattern
            .compile(String.join("|", "\"\"\"[\\s\\S]*?\"\"\"", "\"[^\"\\\\\\n]*(?:\\\\.[^\"\\\\\\n]*)*\"",
                    "'[^'\\\\\\n]*(?:\\\\.[^'\\\\\\n]*)*'", "//[^\\n]*", "/\\*[\\s\\S]*?\\*/"));

    private JavaSource() {
    }

    /** The source with every comment blanked and every literal kept, so a comment's text matches nothing. */
    public static String withoutComments(String source) {

        return COMMENT_OR_LITERAL.matcher(source)
                .replaceAll(token -> token.group().startsWith("/") ? " " : Matcher.quoteReplacement(token.group()));
    }
}
