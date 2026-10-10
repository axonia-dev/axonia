package dev.axonia.installation.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.installation.application.port.out.MailServerSettingsStore;
import dev.axonia.installation.domain.MailField;
import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.SecretField;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MailServerResolver {

    private final MailServerSettingsStore table;

    private final SettingsCipher cipher;

    private final MailServerOverlay overlay;

    public ResolvedMailServer resolve() {

        var row = table.read();
        var sealed = Optional.ofNullable(row.passwordSecret());
        var password = sealed.flatMap(envelope -> cipher.open(SecretField.MAIL_PASSWORD, envelope));
        var unreadable = sealed.isPresent() && password.isEmpty() && !overlay.locks(MailField.PASSWORD);
        return new ResolvedMailServer(overlay.values(), row.values(password.orElse(null)), unreadable, row);
    }
}
