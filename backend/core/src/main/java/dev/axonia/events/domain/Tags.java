package dev.axonia.events.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import dev.axonia.community.api.TagNormalizer;

/**
 * The one cleaning rule for the tags a subject is expressed in: a topic's, or a slot's own. Both feed the same
 * expertise graph, so a tag typed on a slot and the same tag typed on a topic must survive identically.
 *
 * <p>
 * Each tag is stored under the single name the whole product knows it by, the {@link TagNormalizer} slug: {@code Spring
 * Boot} and {@code spring boot} are one tag, {@code spring-boot}, spelled the way the library's artifacts and the
 * expertise projection spell it. The caller's spelling is not preserved, deliberately: a tag is an identity, and a
 * curator who renames or retires one from the vocabulary can only reach the content carrying it if every store names it
 * the same. Blanks and the over-long are dropped, and the count is capped.
 *
 * <p>
 * The caller's order decides only what survives: which ten are kept. The result itself is sorted alphabetically, and
 * that is the canonical order everything downstream uses, because both entities store their tags in a {@code Set}:
 * insertion order is not persisted, so a reload would not reproduce it, and only a total order keeps the stored tags,
 * the read model and the published event saying the same thing.
 */
public final class Tags {

    /** Longer than this is a sentence, not a subject. The tag column is sized to match. */
    private static final int MAX_LENGTH = 50;

    /** Past ten, a talk is not really about any of them. */
    private static final int MAX_COUNT = 10;

    private Tags() {
    }

    public static Set<String> clean(Collection<String> raw) {

        if (raw == null) {
            return Set.of();
        }
        var cleaned = new LinkedHashSet<String>();
        for (var tag : raw) {
            if (tag == null) {
                continue;
            }
            var slug = TagNormalizer.normalize(tag);
            if (slug.isEmpty() || slug.length() > MAX_LENGTH) {
                continue;
            }
            cleaned.add(slug);
            if (cleaned.size() == MAX_COUNT) {
                break;
            }
        }
        return cleaned.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
