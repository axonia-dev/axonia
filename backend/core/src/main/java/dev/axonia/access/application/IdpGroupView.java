package dev.axonia.access.application;

import java.util.List;

/**
 * Read model for one IdP group annotated with its current role bindings. {@code inDirectory} is true when the group was
 * discovered in the resolved directory source, false when it exists only as a value-authored binding.
 *
 * <p>
 * {@code awaitingConfirmation} is true when the directory has pushed a group under this key but it is being held
 * because a binding was already waiting on it, so none of these bindings grant anything yet. It is the only signal the
 * administrator gets that a confirmation is owed, which is why the read model carries it rather than leaving the state
 * discoverable only from the database.
 */
public record IdpGroupView(String groupId, String displayName, boolean inDirectory, boolean awaitingConfirmation,
        List<RoleBinding> bindings) {

    public IdpGroupView {
        bindings = List.copyOf(bindings);
    }
}
