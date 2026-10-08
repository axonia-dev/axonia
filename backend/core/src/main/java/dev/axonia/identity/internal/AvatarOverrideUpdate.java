package dev.axonia.identity.internal;

import lombok.Builder;

/**
 * Column values written when a member points their profile at their own uploaded picture - a parameter object for the
 * targeted repository update, in the same shape as {@link AvatarSyncUpdate}.
 */
@Builder
public record AvatarOverrideUpdate(String organizationId, String userId, String key) {
}
