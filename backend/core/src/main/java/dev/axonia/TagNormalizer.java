package dev.axonia;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The one spelling of a tag, for every store that holds one: the library's artifacts, the events context's topics and
 * slots, and the expertise projection. A tag is an identity, not a label, and a curator can only own a vocabulary whose
 * every occurrence answers to the same name - {@code Spring Boot}, {@code spring boot} and {@code SPRING-BOOT} all name
 * {@code spring-boot}, so a rename or a delete aimed at that name reaches all three.
 *
 * <p>
 * It lives in the root package because it is a product-wide invariant rather than any one context's detail: every
 * module may depend on this package, and none may depend on another's internals.
 */
public final class TagNormalizer {

    private TagNormalizer() {
    }

    public static String normalize(String raw) {

        // 1. lowercase
        var lower = raw.toLowerCase(Locale.ROOT);

        // 2. NFD decompose, remove non-ASCII characters (accent marks)
        var decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        var asciiOnly = decomposed.replaceAll("[^\\x00-\\x7F]", "");

        // 3. replace runs of non-alphanumeric chars with a single "-"
        var hyphenated = asciiOnly.replaceAll("[^a-z0-9]+", "-");

        // 4. strip leading/trailing "-"
        return hyphenated.replaceAll("^-|-$", "");
    }
}
