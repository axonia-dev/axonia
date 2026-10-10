package dev.axonia.shared.api.error;

/**
 * A refusal whose message is meant for the caller: it names the change that makes the call succeed.
 *
 * <p>
 * Most refusals cannot say why. A message that explains a 403 in detail can disclose the existence of a resource, or
 * the shape of a rule, that the caller has no right to learn, so outward-facing surfaces flatten every
 * {@link ForbiddenException} into one deliberately uninformative sentence. That is the right default.
 *
 * <p>
 * Some refusals are the opposite. The caller broke a rule it can already see, by supplying a field it should simply
 * have left out, and the honest answer is to say so. Flattening those is not caution but misdirection: an agent told
 * "you are not a member of this community" when it merely credited a colleague will rationally try to join a community
 * it is already in, rather than drop the field and retry.
 *
 * <p>
 * Throwing this type is a claim about the message, and the only thing that entitles a sanitizer to pass it through
 * verbatim: it discloses nothing the caller does not already hold, and it says what to change.
 */
public class ActionableForbiddenException extends ForbiddenException {

    public ActionableForbiddenException(String message) {

        super(message);
    }
}
