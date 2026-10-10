package dev.axonia.events.adapter.out.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.MemberProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class UserProfileAccessAdapterTest {

    @Mock
    private MemberProfiles userService;

    @InjectMocks
    private UserProfileAccessAdapter sut;

    @Test
    void displayName_returnsDisplayNameWhenFound() {

        when(userService.displayName("default", "u1")).thenReturn(Optional.of("Alice Anderson"));

        assertThat(sut.displayName("default", "u1")).isEqualTo("Alice Anderson");
    }

    @Test
    void displayName_fallsBackToDeletedMemberLabelWhenNotFound() {

        // An erased member's surrogate id (e.g. "erased:<uuid>") has no row left in identity_user.
        // A topic proposed before erasure must still render a neutral label, never that raw id.
        when(userService.displayName("default", "erased:u1")).thenReturn(Optional.empty());

        assertThat(sut.displayName("default", "erased:u1")).isEqualTo("Deleted member");
    }

    @Test
    void displayNames_delegatesToUserService() {

        var ids = List.of("u1", "u2");
        when(userService.displayNames("default", ids)).thenReturn(Map.of("u1", "Alice", "u2", "Bob"));

        assertThat(sut.displayNames("default", ids))
                .containsExactlyInAnyOrderEntriesOf(Map.of("u1", "Alice", "u2", "Bob"));
    }

    @Test
    void displayNames_fallsBackToDeletedMemberLabelForMissingIds() {

        var ids = List.of("u1", "erased:u2");
        when(userService.displayNames("default", ids)).thenReturn(Map.of("u1", "Alice"));

        assertThat(sut.displayNames("default", ids))
                .containsExactlyInAnyOrderEntriesOf(Map.of("u1", "Alice", "erased:u2", "Deleted member"));
    }

    /**
     * The deliberate asymmetry with the two tests above: a name gets a neutral stand-in, a clock does not. A member
     * nobody has measured is absent from the result, which is the one way a caller can tell "no clock" from "this
     * clock".
     */
    @Test
    void timeZones_omitsAnUnknownClockRatherThanFillingItIn() {

        var ids = List.of("u1", "u2");
        when(userService.timeZones("default", ids)).thenReturn(Map.of("u1", "Europe/Berlin"));

        assertThat(sut.timeZones("default", ids)).containsExactlyInAnyOrderEntriesOf(Map.of("u1", "Europe/Berlin"));
    }
}
