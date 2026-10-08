package dev.axonia.installation.internal;

/** Who calls, and the installation's Organization they call in, read from the token once by the controller. */
record MailServerActor(String subject, String organizationId) {
}
