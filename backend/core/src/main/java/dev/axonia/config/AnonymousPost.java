package dev.axonia.config;

/**
 * A POST endpoint, relative to the API's own prefix, that carries its own credential in the request body and so is
 * reachable without a bearer. The module serving it contributes one beside the route; {@link SecurityConfig} permits
 * exactly the contributed ones, and with none every API request needs authentication.
 *
 * <p>
 * The path starts with {@code /}, or it would be glued onto the prefix and silently never match, and names no
 * {@code **}, which would open a whole subtree rather than one endpoint.
 */
public record AnonymousPost(String path) {

    public AnonymousPost {

        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("An anonymous post's path starts with '/': " + path);
        }
        if (path.contains("**")) {
            throw new IllegalArgumentException("An anonymous post names a single path, not a subtree: " + path);
        }
    }
}
