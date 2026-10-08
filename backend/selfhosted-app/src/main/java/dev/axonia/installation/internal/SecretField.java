package dev.axonia.installation.internal;

import java.nio.charset.StandardCharsets;

/** Which stored secret an envelope holds; sealed into it, so an envelope moved to another field never opens. */
record SecretField(String integration, String field) {

    static final SecretField MAIL_PASSWORD = new SecretField("mail", "password");

    byte[] associatedData() {

        return (integration + "/" + field).getBytes(StandardCharsets.UTF_8);
    }
}
