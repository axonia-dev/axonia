package dev.axonia.shared.api.error;

/**
 * A delegated request tried to reach an endpoint whose effect is not confined to the session: exercising a data-subject
 * right on the person's account, minting a credential bound to their subject, or changing who holds what authority.
 * Whoever acts for a person can see what the person sees; that carries neither the person's consent to act as them nor
 * licence to grant authority that outlives the session.
 *
 * <p>
 * In the kernel because more than one module refuses such a request, each for the surface it guards, and
 * {@code GlobalExceptionHandler} maps it once for all of them.
 */
public class OwnSignInRequiredException extends RuntimeException {

    public OwnSignInRequiredException() {

        super("This action needs the person's own sign-in.");
    }
}
