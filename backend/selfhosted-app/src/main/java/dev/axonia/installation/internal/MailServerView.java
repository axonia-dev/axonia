package dev.axonia.installation.internal;

import dev.axonia.communication.MailSecurity;
import lombok.Builder;

@Builder
record MailServerView(String state, long revision, MailServerFieldView<String> host, MailServerFieldView<Integer> port,
        MailServerFieldView<MailSecurity> security, MailServerFieldView<String> username,
        MailServerPasswordView password, MailServerFieldView<String> fromAddress, MailServerFieldView<String> fromName,
        boolean changedSinceTest, MailFailureView lastFailure,
        MailLastChangeView lastChange) implements MailServerSaved {
}
