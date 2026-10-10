package dev.axonia.communication.application;

/**
 * What {@link MailRenderer#render} hands back: a subject line and the two parts a {@code MimeMessageHelper} needs to
 * send a multipart/alternative message - the HTML part for a mail client that renders it, and the plain-text part for
 * one that does not.
 */
public record RenderedMail(String subject, String html, String text) {
}
