package dev.axonia.installation.domain;

/** The deployment's AXONIA_MAIL_* variables as text, unchecked until {@link MailServerOverlay} reads them. */
public interface MailServerVariables {

    String host();

    String port();

    String security();

    String username();

    String password();

    String fromAddress();

    String fromName();
}
