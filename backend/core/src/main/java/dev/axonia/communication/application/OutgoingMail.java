package dev.axonia.communication.application;

import dev.axonia.communication.application.port.out.MailBatch;

/**
 * What {@link OutboxRowProcessor} hands to {@link MailBatch#send}: a resolved address, {@link MailRenderer}'s rendered
 * parts, and the unsubscribe API URL.
 *
 * <p>
 * {@code unsubscribeUrl} is the {@code POST}-only API endpoint - {@code SmtpDelivery} puts it in the
 * {@code List-Unsubscribe} header, never in a rendered part. It is a deliberately different URL from the
 * {@code unsubscribePageUrl} fact {@link OutboxRowProcessor#compose} also writes into {@code facts} for the footer link
 * a person clicks: that one is a {@code GET} to a frontend page, because a {@code POST}-only API answering the footer's
 * click would 405 every time.
 *
 * <p>
 * {@code unsubscribeUrl} stays nullable in shape even though {@link OutboxRowProcessor} now always mints one: every
 * {@link dev.axonia.communication.api.MessageType} this deploy declares is subject to a member's notification
 * preferences, so every row {@code send} reaches carries a real link. The field is nullable anyway - not narrowed to
 * non-null - because {@code SmtpDelivery} is general-purpose: a future message type exempt from unsubscribe (a legal
 * notice, say) is a decision for whoever adds it, not one this record should foreclose by construction.
 */
public record OutgoingMail(String to, String subject, String html, String text, String unsubscribeUrl) {
}
