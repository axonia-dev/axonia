package dev.axonia.communication.api;

/**
 * Why a post did not arrive, in the only two shapes worth telling apart. {@code GONE} means the webhook itself is
 * revoked and retrying is theatre; {@code TRANSIENT} is ordinary weather.
 */
public enum ChatFailureKind {
    GONE, TRANSIENT
}
