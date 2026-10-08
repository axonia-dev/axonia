package dev.axonia;

/**
 * Masks comments and string/char/text-block content out of Java source, so neither can immunise a member from a
 * structural text-scan guard by accident. Every masked character is replaced by a space rather than deleted,
 * <strong>preserving every newline so line numbers stay valid</strong> - load-bearing for both callers, because each
 * reports its findings as {@code file:line} against the masked copy, and for
 * {@link ReadPathTransactionGuardTest#methodExtentEnd}, which counts braces over that copy. Shared verbatim by
 * {@link CrossCommunityReferenceGuardTest} and {@link ReadPathTransactionGuardTest} - originally written for the
 * former, extracted here so both scan the same way and never drift apart.
 */
final class SourceMasking {

    private SourceMasking() {
    }

    static String mask(String source) {

        var out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            var c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (source.startsWith("\"\"\"", i)) {
                out.append("   ");
                i += 3;
                while (i < n && !source.startsWith("\"\"\"", i)) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("   ");
                    i += 3;
                }
            } else if (c == '"' || c == '\'') {
                var quote = c;
                out.append(' ');
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append(' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
