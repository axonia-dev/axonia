package dev.axonia.identity.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.pat.MemberAccounts;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class MemberAccountsAdapter implements MemberAccounts {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String organizationId, String userId) {
        return userRepository.findByOrganizationIdAndUserId(organizationId, userId).map(UserEntity::getActive)
                .orElse(false);
    }
}
