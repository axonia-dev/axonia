package dev.axonia.events.api;

import lombok.Builder;

/**
 * {@code wrapUp} is an explicit answer, never a default: {@code false} means the community is never mailed a wrap-up of
 * this session. {@code recordingUrl} and {@code notes} are the session's follow-up record, either of them optional.
 */
@Builder
public record SessionClosureRequest(boolean wrapUp, String recordingUrl, String notes) {
}
