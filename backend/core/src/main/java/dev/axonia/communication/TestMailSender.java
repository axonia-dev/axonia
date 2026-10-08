package dev.axonia.communication;

/**
 * Sends one message with candidate settings over the transport delivery uses: one connection, one message, at most 15
 * seconds. A refusal is an outcome, never an exception.
 */
public interface TestMailSender {

    MailOutcome send(MailServerSettings candidate, TestMail mail);
}
