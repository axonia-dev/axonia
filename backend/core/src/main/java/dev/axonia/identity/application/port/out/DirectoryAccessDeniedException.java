package dev.axonia.identity.application.port.out;

/**
 * The directory refused the read outright: the credential was not accepted, or it lacks the permission or consent the
 * read needs. Distinct from any other failure because retrying cannot help until an administrator acts.
 */
public class DirectoryAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DirectoryAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
