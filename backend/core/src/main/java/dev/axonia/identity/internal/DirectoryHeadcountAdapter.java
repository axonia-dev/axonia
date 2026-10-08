package dev.axonia.identity.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.organization.DirectoryHeadcount;
import lombok.RequiredArgsConstructor;

/**
 * Answers the organization module's headcount question from {@code identity_user}, the table this module owns. The
 * count runs under the caller's own RLS scope, so it can only ever total the tenant the caller is already in.
 */
@Component
@RequiredArgsConstructor
class DirectoryHeadcountAdapter implements DirectoryHeadcount {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public long presentMembers(String organizationId) {
        return userRepository.countPresentByOrganizationId(organizationId);
    }
}
