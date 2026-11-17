package dev.axonia.identity.application;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.domain.ReadName;
import dev.axonia.identity.domain.UserAccount;

import static org.assertj.core.api.Assertions.assertThat;

class MemberNamesTest {

    private static final String ORG = "default";
    private static final String RECEIVED = "Rochat, Aurélia-NORTHWIND";

    @Test
    void readsThroughTheOrganizationsPatterns() {

        var names = FixedNamePatterns.declaring(ORG, "{family}, {given}-NORTHWIND");

        assertThat(names.read(ORG, RECEIVED)).isEqualTo(new ReadName("Aurélia Rochat", "Aurélia"));
    }

    @Test
    void keepsTheNameWholeWithNoPatterns() {

        assertThat(FixedNamePatterns.none().read(ORG, RECEIVED)).isEqualTo(new ReadName(RECEIVED, null));
    }

    @Test
    void anotherOrganizationsPatternsDoNotApply() {

        var names = FixedNamePatterns.declaring("acme", "{family}, {given}-NORTHWIND");

        assertThat(names.read(ORG, RECEIVED)).isEqualTo(new ReadName(RECEIVED, null));
    }

    @Test
    void namesAnAccountKeepingTheReceivedNameAsAsserted() {

        var names = FixedNamePatterns.declaring(ORG, "{family}, {given}-NORTHWIND");
        var account = UserAccount.builder().organizationId(ORG).displayName("stale").givenName("stale").build();

        var named = names.named(account, RECEIVED);

        assertThat(named.receivedName()).isEqualTo(RECEIVED);
        assertThat(named.displayName()).isEqualTo("Aurélia Rochat");
        assertThat(named.givenName()).isEqualTo("Aurélia");
    }
}
