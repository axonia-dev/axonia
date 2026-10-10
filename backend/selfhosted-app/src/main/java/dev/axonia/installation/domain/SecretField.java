package dev.axonia.installation.domain;

import java.nio.charset.StandardCharsets;

/** Which stored secret an envelope holds; sealed into it, so an envelope moved to another field never opens. */
public record SecretField(String integration, String field) {

    public static final SecretField MAIL_PASSWORD = new SecretField("mail", "password");

    public byte[] associatedData() {

        return (integration + "/" + field).getBytes(StandardCharsets.UTF_8);
    }
}
