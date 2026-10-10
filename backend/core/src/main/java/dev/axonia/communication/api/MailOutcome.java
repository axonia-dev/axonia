package dev.axonia.communication.api;

import java.util.Objects;
import java.util.Optional;

public sealed interface MailOutcome {

    static MailOutcome accepted() {

        return new Accepted();
    }

    static MailOutcome refused(MailRefusal reason) {

        return new Refused(reason, Optional.empty());
    }

    static MailOutcome refused(MailRefusal reason, Optional<String> reply) {

        return new Refused(reason, reply);
    }

    record Accepted() implements MailOutcome {
    }

    /** {@code reply} is the server's own reply line, without control characters, at most 300 characters. */
    record Refused(MailRefusal reason, Optional<String> reply) implements MailOutcome {

        public static final int MAX_REPLY_LENGTH = 300;

        public Refused {
            Objects.requireNonNull(reason, "reason");
            reply = Objects.requireNonNullElse(reply, Optional.<String>empty()).map(Refused::printable)
                    .filter(line -> !line.isEmpty());
        }

        private static String printable(String line) {

            return line.codePoints().filter(c -> !Character.isISOControl(c)).limit(MAX_REPLY_LENGTH)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString()
                    .strip();
        }
    }
}
