package dev.axonia.installation.internal;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class MailServerResolver {

    private final MailServerSettingsTable table;

    private final SettingsCipher cipher;

    private final MailServerOverlay overlay;

    ResolvedMailServer resolve() {

        var row = table.read();
        var sealed = Optional.ofNullable(row.passwordSecret());
        var password = sealed.flatMap(envelope -> cipher.open(SecretField.MAIL_PASSWORD, envelope));
        var unreadable = sealed.isPresent() && password.isEmpty() && !overlay.locks(MailField.PASSWORD);
        return new ResolvedMailServer(overlay.values(), row.values(password.orElse(null)), unreadable, row);
    }
}
