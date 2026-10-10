package dev.axonia.installation.application;

import lombok.Getter;

@Getter
public class MailTestRateLimitedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    MailTestRateLimitedException(long retryAfterSeconds) {

        super("Too many test mails. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
