package dev.axonia.communication.api;

import java.util.Locale;

/** Why a mail server refused, in the seven words the API and the card use. */
public enum MailRefusal {
    CONNECTION_FAILED, TIMEOUT, TLS_REQUIRED, TLS_FAILED, AUTHENTICATION_FAILED, SENDER_REFUSED, RECIPIENT_REFUSED;

    /** {@code connection_failed}, {@code timeout}, and so on: the value the HTTP API carries. */
    public String code() {

        return name().toLowerCase(Locale.ROOT);
    }
}
