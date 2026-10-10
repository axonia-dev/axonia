package dev.axonia.identity.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.pat.MemberAccounts;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class MemberAccountsAdapter implements MemberAccounts {

    private final UserStore userStore;

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String organizationId, String userId) {
        return userStore.findByOrganizationIdAndUserId(organizationId, userId).map(UserAccount::active).orElse(false);
    }
}
