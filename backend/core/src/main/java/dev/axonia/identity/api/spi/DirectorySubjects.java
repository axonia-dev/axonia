package dev.axonia.identity.api.spi;

import java.util.Optional;

/**
 * Port: whether the key a provisioning directory files a person under is also the subject that person will sign in
 * with.
 *
 * <p>
 * When it is, a person the directory pushes holds their subject from the moment they are provisioned: they can be
 * credited, assigned and granted roles before their first sign-in, and that sign-in finds them by subject rather than
 * by address. When it is not, they hold none until a sign-in reconciles them by address.
 */
public interface DirectorySubjects {

    /**
     * The subject {@code externalId} names, in the form a token carries it, or empty when this edition's subjects are
     * not a directory's keys or {@code externalId} is not one of them.
     */
    Optional<String> subjectOf(String externalId);
}
