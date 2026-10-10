package dev.axonia.identity.application;

/**
 * A claim-URL avatar fetch failed in a way that says nothing about the photo itself: a transient network fault, an
 * interrupted request, or a 429/5xx from the provider. {@link AvatarSyncService#sync} catches this alongside every
 * other {@link RuntimeException} and leaves {@code avatar_synced_at} unstamped so the next sync window retries, rather
 * than treating the blip as "no photo" and clearing a good avatar.
 */
public class AvatarFetchFailedException extends RuntimeException {

    public AvatarFetchFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
