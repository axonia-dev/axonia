package dev.axonia.communication.internal;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;

import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailRefusal;
import lombok.experimental.UtilityClass;

/** Reads a JavaMail failure as one of the seven ways a mail server refuses, with the reply line it gave. */
@UtilityClass
class MailFailures {

    private static final int MAX_CAUSE_DEPTH = 10;
    private static final Pattern REPLY_LINE = Pattern.compile("[2-5]\\d\\d([ -].*)?");
    private static final Pattern NAMES_TLS = Pattern.compile("\\b(STARTTLS|TLS|SSL|encryption)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<Integer> AUTHENTICATION_CODES = Set.of(530, 534, 535);
    private static final String MAIL_FROM = "MAIL FROM";
    private static final Set<String> AFTER_DATA = Set.of("DATA", ".");

    MailOutcome outcomeOf(Throwable failure) {

        var chain = chainOf(failure);
        var reply = replyLineOf(chain);
        return MailOutcome.refused(reasonOf(chain, reply), reply);
    }

    MailRefusal reasonOf(Throwable failure) {

        var chain = chainOf(failure);
        return reasonOf(chain, replyLineOf(chain));
    }

    /** True when every message on this connection would meet the same refusal: the account, not the message. */
    boolean refusesEveryMessage(Throwable failure) {

        var chain = chainOf(failure);
        return holds(chain, AuthenticationFailedException.class)
                || refusedAt(chain, command -> command.startsWith(MAIL_FROM));
    }

    private MailRefusal reasonOf(List<Throwable> chain, Optional<String> reply) {

        if (holds(chain, SMTPAddressFailedException.class)) {
            return MailRefusal.RECIPIENT_REFUSED;
        }
        if (holds(chain, SSLException.class)) {
            return MailRefusal.TLS_FAILED;
        }
        var replyNamesTls = reply.filter(MailFailures::namesTls).isPresent();
        if (holds(chain, AuthenticationFailedException.class)) {
            return replyNamesTls ? MailRefusal.TLS_REQUIRED : MailRefusal.AUTHENTICATION_FAILED;
        }
        if (holds(chain, SendFailedException.class)) {
            return sendFailureOf(chain, reply, replyNamesTls);
        }
        if (chain.stream().map(Throwable::getMessage).anyMatch(MailFailures::namesTls)) {
            return MailRefusal.TLS_FAILED;
        }
        return holds(chain, SocketTimeoutException.class) ? MailRefusal.TIMEOUT : MailRefusal.CONNECTION_FAILED;
    }

    /** A TLS demand after DATA is the recipient domain's policy, refusing this message and no other. */
    private MailRefusal sendFailureOf(List<Throwable> chain, Optional<String> reply, boolean replyNamesTls) {

        if (replyNamesTls) {
            return refusedAt(chain, AFTER_DATA::contains) ? MailRefusal.RECIPIENT_REFUSED : MailRefusal.TLS_REQUIRED;
        }
        var demandsAuthentication = reply.map(line -> Integer.parseInt(line.substring(0, 3)))
                .filter(AUTHENTICATION_CODES::contains).isPresent();
        return demandsAuthentication ? MailRefusal.AUTHENTICATION_FAILED : MailRefusal.SENDER_REFUSED;
    }

    private Optional<String> replyLineOf(List<Throwable> chain) {

        return chain.stream().map(Throwable::getMessage).filter(Objects::nonNull)
                .map(message -> message.lines().findFirst().orElse("").strip())
                .filter(line -> REPLY_LINE.matcher(line).matches()).findFirst();
    }

    private boolean refusedAt(List<Throwable> chain, Predicate<String> command) {

        return chain.stream().anyMatch(link -> link instanceof SMTPSendFailedException refusal
                && refusal.getCommand() != null && command.test(refusal.getCommand()));
    }

    /**
     * Read with every address, host and domain taken out first: a refused connection names its host ("Couldn't connect
     * to host, port: tls.acme.com, 587") and a refused sender can echo its address, and neither may read as TLS.
     */
    private boolean namesTls(String message) {

        return message != null && NAMES_TLS.matcher(MailNames.blankedIn(message)).find();
    }

    private boolean holds(List<Throwable> chain, Class<? extends Throwable> type) {

        return chain.stream().anyMatch(type::isInstance);
    }

    private List<Throwable> chainOf(Throwable failure) {

        var chain = new ArrayList<Throwable>();
        for (var link = failure; link != null && chain.size() < MAX_CAUSE_DEPTH; link = link.getCause()) {
            chain.add(link);
        }
        return chain;
    }
}
