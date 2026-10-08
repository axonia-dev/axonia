package dev.axonia;

/**
 * Whether the paid axis is open to somebody in a given Organization. Declared in the kernel because open modules ask it
 * and the answer belongs to the edition: the hosted edition sells it, a self-hosted installation opens it to everyone.
 *
 * <p>
 * One rule with two consumers rather than two copies of the same check: agent access asked it first, the reaction
 * catalog second.
 *
 * <p>
 * Fails closed on an Organization that does not resolve: no answer is not a yes, regardless of what the subject holds.
 */
public interface PaidCapability {

    boolean isOpenTo(String organizationId, String subject);

    boolean isOpenToCaller();

    /** What a member reads when {@code action} is refused because the paid axis is closed to them. */
    String refusal(PaidAction action);
}
