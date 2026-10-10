package dev.axonia.shared.api.personaldata;

/**
 * What kind of personal data a table holds, in the terms a person reading the privacy notice recognises.
 *
 * <p>
 * This is a second axis over the same declarations, not a second registry. {@link Classification} and
 * {@link Disposition} answer what happens to a column when somebody exercises a right - identifier versus content,
 * deleted versus anonymised versus cleared. Article 13 asks a different question: what categories of data are held.
 * {@code SUBJECT} versus {@code PERSONAL_CONTENT} is not "who you are" versus "what you contributed".
 *
 * <p>
 * The category is required at construction, so a table added next year cannot compile without one, and every value used
 * here must have a section in the privacy notice - see {@code personal-data-categories.json} at the repository root and
 * the tests on either side of it.
 *
 * <p>
 * The {@code section} is the anchor of that notice section, and is what both gates match on.
 */
public enum DataCategory {

    /** The person record itself: identifiers, name, email, directory attributes, avatar. */
    WHO_YOU_ARE("who-you-are"),

    /** What a member wrote or produced for their community: topics, artifacts, sessions, resources. */
    WHAT_YOU_CONTRIBUTE("what-you-contribute"),

    /** How a member took part: attendance, sign-ups, votes, appreciation given and received. */
    HOW_YOU_TAKE_PART("how-you-take-part"),

    /** What a member is known for: expertise signals and the tags behind them. */
    WHAT_YOU_KNOW("what-you-know"),

    /** What a member wants to learn: the areas they named, per community. */
    WHAT_YOU_WANT_TO_LEARN("what-you-want-to-learn"),

    /** What a member kept for themselves: bookmarks and saved items. */
    WHAT_YOU_SAVED("what-you-saved"),

    /** Messages a member sent axonia, and what was recorded about answering them. */
    WHEN_YOU_CONTACT_US("when-you-contact-us"),

    /**
     * Mail axonia sends about activity in the communities a member belongs to, and the per-type setting that controls
     * it.
     */
    WHEN_AXONIA_CONTACTS_YOU("when-axonia-contacts-you"),

    /**
     * Credentials and grants: tokens, role bindings, SSO plumbing - and the audit trail of how those credentials were
     * used, such as which tool calls an agent made with them.
     */
    ACCESS_AND_SECURITY("access-and-security"),

    /**
     * What happened when you exercised a right, such as erasure. The record this covers deliberately identifies nobody
     * - no subject, no name, no email, nothing that names or points back to the person - because its whole purpose is
     * to let the fact that an erasure happened survive the erasure itself. Nothing under this category is personal data
     * about the person the right was exercised for; it is proof of an action, kept unlinkable to them on purpose.
     */
    RIGHTS_YOU_EXERCISED("rights-you-exercised");

    private final String section;

    DataCategory(String section) {
        this.section = section;
    }

    /** The anchor of the privacy-notice section describing this category. */
    public String section() {
        return section;
    }
}
