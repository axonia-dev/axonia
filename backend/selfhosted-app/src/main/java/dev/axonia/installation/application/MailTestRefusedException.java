package dev.axonia.installation.application;

import java.util.Optional;

import dev.axonia.communication.api.MailRefusal;
import lombok.Getter;

@Getter
public class MailTestRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final MailRefusal reason;

    private final transient Optional<String> reply;

    MailTestRefusedException(MailRefusal reason, Optional<String> reply) {

        super("The mail server refused the test mail: " + reason.code());
        this.reason = reason;
        this.reply = reply;
    }
}
