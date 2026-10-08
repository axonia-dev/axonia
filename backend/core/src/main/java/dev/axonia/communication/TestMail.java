package dev.axonia.communication;

import java.util.Objects;

/** The message a test sends, composed by its caller: a plain-text body to one recipient. */
public record TestMail(String to, String subject, String text) {

    public TestMail {
        if (!MailServerSettings.isMailAddress(to)) {
            throw new IllegalArgumentException("to must be one bare mail address");
        }
        if (subject == null || subject.isBlank() || MailServerSettings.hasControlCharacter(subject)) {
            throw new IllegalArgumentException("subject must be one line of text");
        }
        Objects.requireNonNull(text, "text");
    }
}
