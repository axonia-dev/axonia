package dev.axonia.events.application;

/**
 * What {@code GET .../message-preview?type=...} answers with: the subject and HTML a send of that message type would
 * produce for the previewing organizer right now. The frontend renders {@code html} inside a sandboxed
 * {@code iframe srcDoc} rather than inline, so a mail template's own styling can never leak into the app and the app's
 * styling can never leak into the preview.
 *
 * <p>
 * One record for every previewable message type rather than one per type: a preview is a subject and a body whichever
 * message it renders, and the request that asked for it already named the type.
 */
public record MessagePreview(String subject, String html) {
}
