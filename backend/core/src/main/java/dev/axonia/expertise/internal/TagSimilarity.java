package dev.axonia.expertise.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class TagSimilarity {

    private TagSimilarity() {
    }

    public record ScoredPair(String tagA, String tagB, double score) {
    }

    /**
     * Returns pairs of tags that are similar enough to suggest merging, sorted by descending score.
     *
     * <p>
     * Algorithm per pair (i &lt; j):
     * <ol>
     * <li>Skip if length difference &gt; 5 (optimization).</li>
     * <li>Compute normalised Levenshtein: {@code 1.0 - editDistance / max(len(a), len(b))}.</li>
     * <li>Apply substring bonus: if one contains the other, score = max(score, 0.7).</li>
     * <li>Include pair when score &ge; threshold.</li>
     * </ol>
     */
    public static List<ScoredPair> suggestMerges(Collection<String> tags, double threshold) {

        var list = new ArrayList<>(tags);
        var result = new ArrayList<ScoredPair>();

        for (var i = 0; i < list.size(); i++) {
            for (var j = i + 1; j < list.size(); j++) {
                var pair = scorePair(list.get(i), list.get(j));
                if (pair != null && pair.score() >= threshold) {
                    result.add(pair);
                }
            }
        }

        result.sort(Comparator.comparingDouble(ScoredPair::score).reversed());
        return result;
    }

    /**
     * Scores a single candidate pair. Returns {@code null} when the pair can be skipped (length-difference
     * optimisation, not a substring).
     */
    private static ScoredPair scorePair(String a, String b) {

        var isSubstring = a.contains(b) || b.contains(a);

        // optimization: skip if lengths differ by more than 5 (unless substring)
        if (!isSubstring && Math.abs(a.length() - b.length()) > 5) {
            return null;
        }

        var maxLen = Math.max(a.length(), b.length());
        double score = maxLen == 0 ? 1.0 : 1.0 - (double) editDistance(a, b) / maxLen;

        if (isSubstring) {
            score = Math.max(score, 0.7);
        }

        return new ScoredPair(a, b, score);
    }

    /** Classic O(mn) Levenshtein edit distance. */
    private static int editDistance(String a, String b) {

        var m = a.length();
        var n = b.length();
        var dp = new int[m + 1][n + 1];

        for (var i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (var j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (var i = 1; i <= m; i++) {
            for (var j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        return dp[m][n];
    }
}
