package dev.axonia.identity;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdpProviderTest {

    @Test
    void key_returnsTheLowerCaseRoutingKey() {

        // when
        var actual = IdpProvider.ENTRA.key();

        // then
        assertThat(actual).isEqualTo("entra");
    }

    @Test
    void fromKey_resolvesAKnownKey() {

        // when
        var actual = IdpProvider.fromKey("generic_oidc");

        // then
        assertThat(actual).isEqualTo(IdpProvider.GENERIC_OIDC);
    }

    @Test
    void fromKey_rejectsAnUnknownKey() {

        // then
        assertThatThrownBy(() -> IdpProvider.fromKey("ping")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown IdP provider key: ping");
    }

    @Test
    void fromStoredName_resolvesAKnownName() {

        // when
        var actual = IdpProvider.fromStoredName("ENTRA");

        // then
        assertThat(actual).isEqualTo(IdpProvider.ENTRA);
    }

    @Test
    void fromStoredName_resolvesAKnownNameIgnoringCase() {

        // when
        var actual = IdpProvider.fromStoredName(" entra ");

        // then
        assertThat(actual).isEqualTo(IdpProvider.ENTRA);
    }

    @Test
    void fromStoredName_defaultsToGenericOidcOnAnUnknownName() {

        // when
        var actual = IdpProvider.fromStoredName("ping");

        // then
        assertThat(actual).isEqualTo(IdpProvider.GENERIC_OIDC);
    }

    @Test
    void fromStoredName_defaultsToGenericOidcOnNull() {

        // when
        var actual = IdpProvider.fromStoredName(null);

        // then
        assertThat(actual).isEqualTo(IdpProvider.GENERIC_OIDC);
    }

    @Test
    void fromStoredName_defaultsToGenericOidcOnBlank() {

        // when
        var actual = IdpProvider.fromStoredName("   ");

        // then
        assertThat(actual).isEqualTo(IdpProvider.GENERIC_OIDC);
    }

    @Test
    void everyStoredValueOfTheOldEnumStillReads() {

        // then - ENTRA is untouched, a row V90 rewrote reads as itself, and a row V90 has not
        // reached yet degrades to the same generic value rather than 500ing the whole page
        assertThat(IdpProvider.fromStoredName("ENTRA")).isEqualTo(IdpProvider.ENTRA);
        assertThat(IdpProvider.fromStoredName("GENERIC_OIDC")).isEqualTo(IdpProvider.GENERIC_OIDC);
        assertThat(IdpProvider.fromStoredName("KEYCLOAK")).isEqualTo(IdpProvider.GENERIC_OIDC);
    }

    @Test
    void onlyEntraFederatesAMicrosoftDirectory() {

        // then
        assertThat(IdpProvider.ENTRA.federatesMicrosoftDirectory()).isTrue();
        assertThat(IdpProvider.OKTA.federatesMicrosoftDirectory()).isFalse();
        assertThat(IdpProvider.GOOGLE.federatesMicrosoftDirectory()).isFalse();
        assertThat(IdpProvider.GENERIC_OIDC.federatesMicrosoftDirectory()).isFalse();
        assertThat(IdpProvider.GENERIC_SAML.federatesMicrosoftDirectory()).isFalse();
    }

    @Test
    void key_isTheEnumNameLowerCasedForEveryValue() {

        // then
        assertThat(IdpProvider.values()).allSatisfy(provider -> {
            assertThat(provider.key()).isEqualTo(provider.name().toLowerCase(Locale.ROOT));
            assertThat(IdpProvider.fromKey(provider.key())).isEqualTo(provider);
        });
    }
}
