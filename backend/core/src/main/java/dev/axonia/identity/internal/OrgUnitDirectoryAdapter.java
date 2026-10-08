package dev.axonia.identity.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.OrgUnit;
import dev.axonia.identity.OrgUnitDirectory;
import lombok.RequiredArgsConstructor;

/**
 * Reads the synced org-unit labels written by SCIM provisioning to satisfy the {@link OrgUnitDirectory} port.
 */
@Component
@RequiredArgsConstructor
class OrgUnitDirectoryAdapter implements OrgUnitDirectory {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OrgUnit> teamOf(String organizationId, String userId) {

        return userRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(OrgUnitDirectoryAdapter::toOrgUnit).filter(unit -> !unit.isEmpty());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, OrgUnit> teamsOf(String organizationId, Collection<String> userIds) {

        Map<String, OrgUnit> result = new LinkedHashMap<>();
        for (UserEntity user : userRepository.findByOrganizationIdAndUserIdIn(organizationId, userIds)) {
            OrgUnit unit = toOrgUnit(user);
            if (!unit.isEmpty()) {
                result.put(user.getUserId(), unit);
            }
        }
        return result;
    }

    private static OrgUnit toOrgUnit(UserEntity user) {

        return new OrgUnit(user.getBusinessUnit(), user.getDepartment(), user.getTeam());
    }
}
