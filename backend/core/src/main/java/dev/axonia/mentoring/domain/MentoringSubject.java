package dev.axonia.mentoring.domain;

/**
 * What a mentoring notice is about: a proposal or a pairing, the occasion that tells two notices of one kind apart, and
 * the member the notice names, written {@code proposal:<id>:<aboutUserId>} or {@code pairing:<id>:<aboutUserId>}, with
 * {@code @<occasion>} after the id when there is one. The occasion is an epoch millisecond or an ISO date, so it never
 * holds a colon. The member is whatever follows the second colon, so their erasure finds every notice about them by its
 * suffix.
 */
public record MentoringSubject(Kind kind, long id, String occasion, String aboutUserId) {

    private static final char SEPARATOR = ':';

    private static final char OCCASION = '@';

    public enum Kind {
        PROPOSAL("proposal"), PAIRING("pairing");

        private final String prefix;

        Kind(String prefix) {

            this.prefix = prefix;
        }

        static Kind ofPrefix(String prefix) {

            for (var kind : values()) {
                if (kind.prefix.equals(prefix)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Not a mentoring subject: " + prefix);
        }
    }

    public static MentoringSubject proposal(long id, String aboutUserId) {

        return proposal(id, null, aboutUserId);
    }

    public static MentoringSubject proposal(long id, String occasion, String aboutUserId) {

        return new MentoringSubject(Kind.PROPOSAL, id, occasion, aboutUserId);
    }

    public static MentoringSubject pairing(long id, String aboutUserId) {

        return pairing(id, null, aboutUserId);
    }

    public static MentoringSubject pairing(long id, String occasion, String aboutUserId) {

        return new MentoringSubject(Kind.PAIRING, id, occasion, aboutUserId);
    }

    public static MentoringSubject parse(String ref) {

        var afterKind = ref.indexOf(SEPARATOR);
        var afterId = ref.indexOf(SEPARATOR, afterKind + 1);
        var idAndOccasion = ref.substring(afterKind + 1, afterId);
        var at = idAndOccasion.indexOf(OCCASION);
        var id = at < 0 ? idAndOccasion : idAndOccasion.substring(0, at);
        var occasion = at < 0 ? null : idAndOccasion.substring(at + 1);
        return new MentoringSubject(Kind.ofPrefix(ref.substring(0, afterKind)), Long.parseLong(id), occasion,
                ref.substring(afterId + 1));
    }

    /** How every subject about the member ends, so their erasure can find them all. */
    public static String aboutSuffix(String userId) {

        return SEPARATOR + userId;
    }

    public String ref() {

        var occasionPart = occasion == null ? "" : OCCASION + occasion;
        return kind.prefix + SEPARATOR + id + occasionPart + aboutSuffix(aboutUserId);
    }
}
