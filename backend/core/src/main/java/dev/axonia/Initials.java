package dev.axonia;

/**
 * A display name reduced to the one or two letters a monogram shows. Shared kernel rather than a copy per bounded
 * context: two fact assemblers already build monograms for the same mail shell - {@code events} for a session's
 * presenters and its sending community, {@code roundup} for the organization a quarterly digest comes from - and the
 * correctness this has to get right is not the kind that survives being written twice.
 */
public final class Initials {

    private static final int MAX_LETTERS = 2;

    private Initials() {
    }

    /**
     * Up to two letters, one per word - "Ada Lovelace" to "AL", "Ada" to "A". Reads whole code points, not
     * {@code char}s: a first letter outside the Basic Multilingual Plane is a surrogate pair, and slicing it at
     * {@code charAt(0)} would put a lone, unpaired surrogate straight into a member's inbox. The "up to two" cap counts
     * code points, not {@code char}s either - a {@link StringBuilder#length()} check would stop after one
     * supplementary-plane letter, since that single letter already occupies two {@code char}s.
     */
    public static String of(String name) {

        var initials = new StringBuilder();
        var codePointCount = 0;
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && codePointCount < MAX_LETTERS) {
                var codePoint = Character.toUpperCase(word.codePointAt(0));
                initials.append(Character.toChars(codePoint));
                codePointCount++;
            }
        }
        return initials.toString();
    }
}
