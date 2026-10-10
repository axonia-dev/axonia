package dev.axonia.installation.application;

/** Who calls, and the installation's Organization they call in, read from the token once by the controller. */
public record MailServerActor(String subject, String organizationId) {
}
