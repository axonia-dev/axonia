package dev.axonia.installation.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Builder;

/**
 * The deployment's AXONIA_MAIL_* variables as text, set in no file. Checked by {@link MailServerOverlay}, never by bean
 * validation, whose start failure would print the value.
 */
@Builder
@ConfigurationProperties("axonia.mail")
record MailServerOverlayProperties(String host, String port, String security, String username, String password,
        String fromAddress, String fromName) {

    @Override
    public String toString() {

        return "MailServerOverlayProperties[host=" + host + ", port=" + port + ", security=" + security + ", username="
                + username + ", password=" + (password == null ? "null" : "<hidden>") + ", fromAddress=" + fromAddress
                + ", fromName=" + fromName + "]";
    }
}
