package dev.axonia.installation.application;

import dev.axonia.communication.api.MailSecurity;
import lombok.Builder;

@Builder
public record MailServerView(String state, long revision, MailServerFieldView<String> host,
        MailServerFieldView<Integer> port, MailServerFieldView<MailSecurity> security,
        MailServerFieldView<String> username, MailServerPasswordView password, MailServerFieldView<String> fromAddress,
        MailServerFieldView<String> fromName, boolean changedSinceTest, MailFailureView lastFailure,
        MailLastChangeView lastChange) implements MailServerSaved {
}
