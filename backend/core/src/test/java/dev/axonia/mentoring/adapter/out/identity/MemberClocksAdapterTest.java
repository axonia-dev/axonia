package dev.axonia.mentoring.adapter.out.identity;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.MemberProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@MockitoSettings
class MemberClocksAdapterTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Mock
    private MemberProfiles memberProfiles;

    @Test
    void eachMemberReadsTheirOwnZone() {

        // given
        var sut = new MemberClocksAdapter(memberProfiles);
        when(memberProfiles.timeZones("org-1", List.of("mia", "max", "mia")))
                .thenReturn(Map.of("mia", "Europe/Zurich", "max", "America/New_York"));

        // when
        var actual = sut.zonesOf("org-1", List.of("mia", "max", "mia"));

        // then
        assertThat(actual).containsOnly(entry("mia", ZoneId.of("Europe/Zurich")),
                entry("max", ZoneId.of("America/New_York")));
    }

    @Test
    void aMemberWithoutAZoneReadsUtc() {

        // given
        var sut = new MemberClocksAdapter(memberProfiles);
        when(memberProfiles.timeZones("org-1", List.of("noah"))).thenReturn(Map.of());

        // when
        var actual = sut.zoneOf("org-1", "noah");

        // then
        assertThat(actual).isEqualTo(UTC);
    }

    @Test
    void anUnknownZoneIdReadsUtc() {

        // given
        var sut = new MemberClocksAdapter(memberProfiles);
        when(memberProfiles.timeZones("org-1", List.of("lea"))).thenReturn(Map.of("lea", "Mars/Olympus_Mons"));

        // when
        var actual = sut.zoneOf("org-1", "lea");

        // then
        assertThat(actual).isEqualTo(UTC);
    }
}
