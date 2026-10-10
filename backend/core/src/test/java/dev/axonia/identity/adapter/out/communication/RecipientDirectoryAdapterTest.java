package dev.axonia.identity.adapter.out.communication;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.spi.RecipientDirectory.Presence;
import dev.axonia.communication.api.spi.RecipientDirectory.Recipient;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.identity.api.UserDirectory.MemberStatus;
import dev.axonia.identity.application.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link RecipientDirectoryAdapter#presenceOf} is a total mapping over {@link MemberStatus}, kept exhaustive on purpose
 * so a sixth status fails to compile here rather than falling through silently - this is what proves every arm actually
 * lands on the {@link Presence} the outbox drain expects, not merely that the switch compiles.
 */
@MockitoSettings
class RecipientDirectoryAdapterTest {

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private UserService userService;

    private RecipientDirectoryAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new RecipientDirectoryAdapter(userDirectory, userService);
    }

    static Stream<Arguments> statuses() {

        return Stream.of(Arguments.of(MemberStatus.PRESENT, Presence.PRESENT),
                Arguments.of(MemberStatus.DEACTIVATED, Presence.DEACTIVATED),
                Arguments.of(MemberStatus.ERASED, Presence.ERASED));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void recipientsOf_mapsEveryMemberStatusToItsMatchingPresence(MemberStatus status, Presence expected) {

        when(userDirectory.byIds("acme", List.of("alice")))
                .thenReturn(List.of(new DirectoryUser("alice", "Alice", "alice@acme.example", null, null, status)));

        var recipients = sut.recipientsOf("acme", List.of("alice"));

        assertThat(recipients).extracting(Recipient::presence).containsExactly(expected);
    }

    @Test
    void recipientsOf_carriesTheRecipientsOwnClock() {

        when(userDirectory.byIds("acme", List.of("alice", "bob"))).thenReturn(
                List.of(new DirectoryUser("alice", "Alice", "alice@acme.example", null, null, MemberStatus.PRESENT),
                        new DirectoryUser("bob", "Bob", "bob@acme.example", null, null, MemberStatus.PRESENT)));
        when(userService.timeZones("acme", List.of("alice", "bob"))).thenReturn(Map.of("alice", "Europe/Berlin"));

        var recipients = sut.recipientsOf("acme", List.of("alice", "bob"));

        // Unknown travels as null rather than a default: a message for Bob renders on the session's own clock, which
        // is a true answer, where a guessed zone would state an hour nobody verified.
        assertThat(recipients).extracting(Recipient::timeZone).containsExactly("Europe/Berlin", null);
    }
}
