package dev.axonia.privacy.application;

import java.util.List;

/**
 * Whether {@link AccountErasure#erase(String)} may proceed, and if not, every reason at once - so a caller such as the
 * erasure-preflight endpoint can render every blocker in one dialog rather than failing one guard at a time. Preflight
 * informs; the write ({@link AccountErasure#erase(String)}) re-checks and decides.
 */
public record ErasurePreflight(boolean permitted, List<ErasureBlocker> blockers) {
}
