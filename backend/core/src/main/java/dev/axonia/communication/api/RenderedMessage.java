package dev.axonia.communication.api;

/**
 * What {@link MessageRenderPort#render} hands back: a subject line and the HTML part of a message, rendered for
 * something other than delivery. Unlike {@code application.RenderedMail}, this carries no plain-text part - the one
 * caller so far, a session wrap-up preview, renders {@code html} inside a sandboxed {@code iframe} and has no use for a
 * text alternative a browser will never show.
 */
public record RenderedMessage(String subject, String html) {
}
