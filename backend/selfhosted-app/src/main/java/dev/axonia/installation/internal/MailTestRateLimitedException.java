package dev.axonia.installation.internal;

import lombok.Getter;

@Getter
class MailTestRateLimitedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    MailTestRateLimitedException(long retryAfterSeconds) {

        super("Too many test mails. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
