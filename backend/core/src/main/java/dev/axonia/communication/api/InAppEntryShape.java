package dev.axonia.communication.api;

/**
 * Which two frozen facts make the line and the link of a member's panel entry for a type the open modules do not send.
 * The sender and headline facts are the ones every type carries. A module sending such a type declares its shape as a
 * bean, beside the policy that claims the type.
 *
 * <p>
 * {@code privateToMember} marks a type whose entries only the member reads: a session acting for the member sees none
 * of them, and they count toward no badge it is shown.
 */
public record InAppEntryShape(MessageType type, String lineFact, String actionUrlFact, boolean privateToMember) {

    public InAppEntryShape(MessageType type, String lineFact, String actionUrlFact) {

        this(type, lineFact, actionUrlFact, false);
    }
}
