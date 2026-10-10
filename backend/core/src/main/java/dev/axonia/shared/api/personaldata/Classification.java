package dev.axonia.shared.api.personaldata;

/** What a column holds, for the purposes of a data-subject request. */
public enum Classification {

    /** An identifier for a person: an identity provider subject, a SCIM id, a surrogate. */
    SUBJECT,

    /** Data about a person that is not an identifier: their name, email, avatar key, or text they authored. */
    PERSONAL_CONTENT
}
