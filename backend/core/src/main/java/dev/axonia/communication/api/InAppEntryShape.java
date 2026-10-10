package dev.axonia.communication.api;

/**
 * Which two frozen facts make the line and the link of a member's panel entry for a type the open modules do not send.
 * The sender and headline facts are the ones every type carries. A module sending such a type declares its shape as a
 * bean, beside the policy that claims the type.
 */
public record InAppEntryShape(MessageType type, String lineFact, String actionUrlFact) {
}
