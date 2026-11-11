package dev.axonia.communication.api;

/**
 * What a sender erases from the record of its expired messages. The subject is the sender's to read: a sender whose
 * subject names a person forgets that person's subjects when they are erased.
 */
public interface ExpiredSubjects {

    /**
     * Deletes the organization's expired-message records of {@code type} whose subject ends with {@code suffix}, and
     * returns how many went.
     */
    int forgetEndingWith(String organizationId, MessageType type, String suffix);
}
