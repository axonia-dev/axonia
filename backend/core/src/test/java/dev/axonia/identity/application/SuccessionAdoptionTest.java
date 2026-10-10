package dev.axonia.identity.application;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.spi.SuccessionAddresses;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserArrival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The directory door adopts the member a running succession maps the push to, and nobody when none is running. Whether
 * one is running, and what it maps an address to, is {@link SuccessionAddresses}' own suite's to prove.
 */
@MockitoSettings
class SuccessionAdoptionTest {

    private static final String ORG = "default";
    private static final Instant BORN = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private SuccessionAddresses successions;

    @Mock
    private UserStore users;

    @InjectMocks
    private SuccessionAdoption sut;

    @Test
    void noRunningSuccessionAdoptsNobody() {

        // given
        when(successions.formerAddressOf(ORG, "ada@new.ch")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.adopt(ORG, push("ada@new.ch"))).isEmpty();
        verify(users, never()).findActiveByEmail(anyString(), anyString());
    }

    @Test
    void anAddressTheSuccessionMapsToNobodyAdoptsNobody() {

        // given
        when(successions.formerAddressOf(ORG, "newcomer@new.ch")).thenReturn(Optional.of("newcomer@old.ch"));
        when(users.findActiveByEmail(ORG, "newcomer@old.ch")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.adopt(ORG, push("newcomer@new.ch"))).isEmpty();
        verify(users, never()).save(any());
    }

    /** The whole point: the resource this answers with is the one the member already was, carrying what was pushed. */
    @Test
    void adoptsTheMemberAtTheAddressTheSuccessionMapsThePushTo() {

        // given
        when(successions.formerAddressOf(ORG, "ada@new.ch")).thenReturn(Optional.of("ada@old.ch"));
        when(users.findActiveByEmail(ORG, "ada@old.ch")).thenReturn(Optional.of(UserAccount.builder().id(7L)
                .organizationId(ORG).userId("sub-ada").scimId("scim-ada").externalId("okta-1").displayName("Ada")
                .email("ada@old.ch").active(true).arrival(UserArrival.FIRST_SIGN_IN).createdAt(BORN).build()));
        when(users.save(any())).thenAnswer(saved -> saved.getArgument(0));

        // when
        var adopted = sut.adopt(ORG, push("ada@new.ch")).orElseThrow();

        // then
        assertThat(adopted.id()).isEqualTo(7L);
        assertThat(adopted.scimId()).isEqualTo("scim-ada");
        assertThat(adopted.userId()).isEqualTo("sub-ada");
        assertThat(adopted.email()).isEqualTo("ada@new.ch");
        assertThat(adopted.externalId()).isEqualTo("entra-1");
        assertThat(adopted.displayName()).isEqualTo("Ada Lovelace");
        assertThat(adopted.businessUnit()).isEqualTo("Engineering");
        assertThat(adopted.department()).isEqualTo("Platform");
        assertThat(adopted.team()).isEqualTo("Identity");
    }

    private static SuccessionAdoption.DirectoryPush push(String email) {

        return SuccessionAdoption.DirectoryPush.builder().email(email).externalId("entra-1").displayName("Ada Lovelace")
                .businessUnit("Engineering").department("Platform").team("Identity").build();
    }
}
