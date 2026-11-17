package dev.axonia.identity.application.scim;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.adapter.out.persistence.JdbcUserStore;
import dev.axonia.identity.adapter.out.persistence.UserEntity;
import dev.axonia.identity.adapter.out.persistence.UserRepository;
import dev.axonia.identity.api.spi.DirectorySubjects;
import dev.axonia.identity.api.spi.SuccessionAddresses;
import dev.axonia.identity.application.FixedNamePatterns;
import dev.axonia.identity.application.SuccessionAdoption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A directory's push stores the name it asserted untouched, shows what the Organization's name pattern reads out of it,
 * and reads back the asserted name, so the directory never sees drift. Assembled by hand over the real store, so the
 * class joins the plain JDBC context rather than caching one of its own.
 */
class ScimUserNamesJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "scim-names-org";
    private static final ScimPrincipal PRINCIPAL = new ScimPrincipal(ORG);

    @Autowired
    private UserRepository userRepository;

    private ScimUserService sut;

    @BeforeEach
    void assembleService() {

        var store = new JdbcUserStore(userRepository);
        var names = FixedNamePatterns.declaring(ORG, "{family}, {given}-NORTHWIND");
        var successions = mock(SuccessionAddresses.class);
        when(successions.formerAddressOf(anyString(), any())).thenReturn(Optional.empty());
        var subjects = mock(DirectorySubjects.class);
        when(subjects.subjectOf(any())).thenReturn(Optional.empty());
        sut = new ScimUserService(store, new ScimUserMapper(), new SuccessionAdoption(successions, store, names),
                new DirectorySubjectClaim(subjects, store), mock(ApplicationEventPublisher.class), names);
    }

    @Test
    void aPushedNameIsShownTidiedAndReadBackAsAsserted() {

        // when
        var created = sut.create(PRINCIPAL, push("Rochat, Aurélia-NORTHWIND"));

        // then
        var stored = stored(created.id());
        assertThat(stored.getReceivedName()).isEqualTo("Rochat, Aurélia-NORTHWIND");
        assertThat(stored.getDisplayName()).isEqualTo("Aurélia Rochat");
        assertThat(stored.getGivenName()).isEqualTo("Aurélia");
        assertThat(created.displayName()).isEqualTo("Rochat, Aurélia-NORTHWIND");
        assertThat(sut.get(PRINCIPAL, created.id()).displayName()).isEqualTo("Rochat, Aurélia-NORTHWIND");
    }

    @Test
    void aPatchedNameIsReadAgain() {

        // given
        var created = sut.create(PRINCIPAL, push("Rochat, Aurélia-NORTHWIND"));

        // when
        sut.patch(PRINCIPAL, created.id(), new ScimPatchRequest(List.of(),
                List.of(new ScimPatchOperation("replace", "displayName", "Rochat, Élise-NORTHWIND"))));

        // then
        var stored = stored(created.id());
        assertThat(stored.getReceivedName()).isEqualTo("Rochat, Élise-NORTHWIND");
        assertThat(stored.getDisplayName()).isEqualTo("Élise Rochat");
        assertThat(stored.getGivenName()).isEqualTo("Élise");
        assertThat(sut.get(PRINCIPAL, created.id()).displayName()).isEqualTo("Rochat, Élise-NORTHWIND");
    }

    @Test
    void aReplacedNameIsReadAgainAndANameNoPatternFitsIsKeptWhole() {

        // given
        var created = sut.create(PRINCIPAL, push("Rochat, Aurélia-NORTHWIND"));

        // when
        sut.replace(PRINCIPAL, created.id(), push("octocat"));

        // then
        var stored = stored(created.id());
        assertThat(stored.getReceivedName()).isEqualTo("octocat");
        assertThat(stored.getDisplayName()).isEqualTo("octocat");
        assertThat(stored.getGivenName()).isNull();
    }

    @Test
    void aPatchThatLeavesTheNameAloneKeepsIt() {

        // given
        var created = sut.create(PRINCIPAL, push("Rochat, Aurélia-NORTHWIND"));

        // when
        sut.patch(PRINCIPAL, created.id(),
                new ScimPatchRequest(List.of(), List.of(new ScimPatchOperation("replace", "active", false))));

        // then
        var stored = stored(created.id());
        assertThat(stored.getReceivedName()).isEqualTo("Rochat, Aurélia-NORTHWIND");
        assertThat(stored.getDisplayName()).isEqualTo("Aurélia Rochat");
    }

    private UserEntity stored(String scimId) {

        return userRepository.findByOrganizationIdAndScimId(ORG, scimId).orElseThrow();
    }

    private static ScimUser push(String displayName) {

        return ScimUser.builder().userName("a.rochat@names.test").displayName(displayName).active(true).build();
    }
}
