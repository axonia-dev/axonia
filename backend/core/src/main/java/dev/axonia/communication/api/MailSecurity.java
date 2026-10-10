package dev.axonia.communication.api;

/** STARTTLS upgrades a plain connection and refuses to go on without it; TLS is TLS from the first byte. */
public enum MailSecurity {
    STARTTLS, TLS, NONE
}
