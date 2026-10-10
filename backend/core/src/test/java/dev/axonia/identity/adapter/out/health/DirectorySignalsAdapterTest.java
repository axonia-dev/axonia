package dev.axonia.identity.adapter.out.health;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.health.api.OrgUnitLabels;
import dev.axonia.identity.application.port.out.OrgUnitDirectory;
import dev.axonia.identity.domain.OrgUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class DirectorySignalsAdapterTest {

    private static final String ORGANIZATION = "default";

    @Mock
    private OrgUnitDirectory orgUnitDirectory;

    @InjectMocks
    private DirectorySignalsAdapter sut;

    @Test
    void unitsOf_mapsEachOrgUnitToHealthLocalLabels() {

        // given the directory returns full org-units for both contributors
        when(orgUnitDirectory.teamsOf(ORGANIZATION, List.of("sub-1", "sub-2"))).thenReturn(Map.of("sub-1",
                new OrgUnit("Acme", "Platform", "Payments"), "sub-2", new OrgUnit("Acme", "Mobile", "iOS")));

        // when
        var result = sut.unitsOf(ORGANIZATION, List.of("sub-1", "sub-2"));

        // then each is mapped to the health-local shape, keys preserved
        assertThat(result).hasSize(2);
        assertThat(result.get("sub-1")).isEqualTo(new OrgUnitLabels("Acme", "Platform", "Payments"));
        assertThat(result.get("sub-2").team()).isEqualTo("iOS");
    }

    @Test
    void unitsOf_noSyncedUnits_isEmpty() {

        // given a self-serve organization where nobody has a unit
        when(orgUnitDirectory.teamsOf(ORGANIZATION, List.of("sub-1"))).thenReturn(Map.of());

        // then the port yields an empty map (Reach will read this as unavailable)
        assertThat(sut.unitsOf(ORGANIZATION, List.of("sub-1"))).isEmpty();
    }
}
