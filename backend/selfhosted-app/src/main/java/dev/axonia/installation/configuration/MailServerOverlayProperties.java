package dev.axonia.installation.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.MailServerVariables;
import lombok.Builder;

/**
 * The deployment's AXONIA_MAIL_* variables as text, set in no file. Checked by {@link MailServerOverlay}, never by bean
 * validation, whose start failure would print the value.
 */
@Builder
@ConfigurationProperties("axonia.mail")
public record MailServerOverlayProperties(String host, String port, String security, String username, String password,
        String fromAddress, String fromName) implements MailServerVariables {

    @Override
    public String toString() {

        return "MailServerOverlayProperties[host=" + host + ", port=" + port + ", security=" + security + ", username="
                + username + ", password=" + (password == null ? "null" : "<hidden>") + ", fromAddress=" + fromAddress
                + ", fromName=" + fromName + "]";
    }
}
