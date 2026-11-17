package dev.axonia.communication.api;

import java.util.Map;
import java.util.function.Predicate;

import lombok.Builder;

/**
 * Which enqueued message {@link MessageRenderPort#renderEnqueued(EnqueuedRender)} renders, and for whom.
 * {@code stillCurrent} lets a caller whose template has outgrown an older fact shape decline a row frozen in that
 * shape; a declined row answers empty, the same as no row.
 */
@Builder
public record EnqueuedRender(String organizationId, String previewingUserId, MessageType type, String subjectRef,
        Predicate<Map<String, Object>> stillCurrent) {

    public EnqueuedRender {
        stillCurrent = stillCurrent == null ? facts -> true : stillCurrent;
    }
}
