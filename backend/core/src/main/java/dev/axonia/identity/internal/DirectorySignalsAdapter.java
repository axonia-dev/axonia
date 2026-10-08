package dev.axonia.identity.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.health.DirectorySignals;
import dev.axonia.health.OrgUnitLabels;
import dev.axonia.identity.OrgUnit;
import dev.axonia.identity.OrgUnitDirectory;
import lombok.RequiredArgsConstructor;

/**
 * Identity's implementation of the community-health directory signal port. Delegates to {@link OrgUnitDirectory} and
 * maps each {@link OrgUnit} to the health-local {@link OrgUnitLabels}, so health's core never imports an identity type.
 */
@Component
@RequiredArgsConstructor
class DirectorySignalsAdapter implements DirectorySignals {

    private final OrgUnitDirectory orgUnitDirectory;

    @Override
    public Map<String, OrgUnitLabels> unitsOf(String organizationId, Collection<String> userIds) {

        Map<String, OrgUnitLabels> result = new LinkedHashMap<>();
        orgUnitDirectory.teamsOf(organizationId, userIds).forEach((userId, unit) -> result.put(userId, toLabels(unit)));
        return result;
    }

    private static OrgUnitLabels toLabels(OrgUnit unit) {

        return new OrgUnitLabels(unit.businessUnit(), unit.department(), unit.team());
    }
}
