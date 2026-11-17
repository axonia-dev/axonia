package dev.axonia.identity.application;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.application.port.out.NamePatternLock;
import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.domain.NamePatterns;
import dev.axonia.identity.domain.ReadName;
import dev.axonia.identity.domain.UserAccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
    void holdsThePatternsSharedBeforeReadingThem() {

        var store = mock(NamePatternStore.class);
        var lock = mock(NamePatternLock.class);
        when(store.find(ORG)).thenReturn(NamePatterns.NONE);

        new MemberNames(store, lock).read(ORG, RECEIVED);

        var order = inOrder(lock, store);
        order.verify(lock).shared(ORG);
        order.verify(store).find(ORG);
        verifyNoMoreInteractions(lock);
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
