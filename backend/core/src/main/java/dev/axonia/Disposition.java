package dev.axonia;

/** What an erasure does with a personal column. */
public enum Disposition {

    /** The whole row goes: it exists only to describe this person. */
    DELETE_ROW,

    /** The row survives with the person replaced by the erasure's surrogate: community history, unnamed. */
    ANONYMISE,

    /** The column is nulled: a live pointer to a person nobody will now honour, not a historical fact. */
    CLEAR,

    /**
     * No per-column rule erases this one; it is declared so that an export carries it. The row itself may still be
     * erased, by a rule the column does not carry: {@code communication_message.facts} is deleted when the message's
     * last delivery is, and a member's erasure alone must not touch it, because the row is equally every other
     * recipient's record of what they were sent. Never read this as "the data survives forever" - read it as "erasing
     * this column is not one member's decision".
     */
    EXPORT_ONLY,

    /**
     * Personal, but not the member's: it identifies an axonia operator, and sits outside the member data-subject
     * regime. No erasure touches it and no export carries it. The platform's own record of who acted on a tenant, on a
     * row that outlives every member of it.
     */
    RETAIN
}
