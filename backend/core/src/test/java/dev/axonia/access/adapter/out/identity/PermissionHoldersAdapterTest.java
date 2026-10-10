package dev.axonia.access.adapter.out.identity;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.Permission;
import dev.axonia.access.application.KnownPermissions;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.identity.api.DirectoryGroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the adapter adds over the store: it refuses a code this edition does not know, and merges the subjects bound
 * directly with the users of the groups bound. The join itself is proven against Postgres in
 * {@code RoleBindingRepositoryTest}; {@link DirectoryGroups} is proven in {@code identity}'s own tree.
 */
@MockitoSettings
class PermissionHoldersAdapterTest {

    private static final String ORGANIZATION = "acme";
    private static final String SCIM_WRITE = Permission.SCIM_WRITE.code();

    private final RoleBindingStore bindings = mock(RoleBindingStore.class);
    private final DirectoryGroups directoryGroups = mock(DirectoryGroups.class);
    private final PermissionHoldersAdapter adapter = new PermissionHoldersAdapter(bindings, directoryGroups,
            new KnownPermissions(List.of()));

    @Test
    void findsTheSubjectsBoundDirectly() {

        when(bindings.subjectsHolding(ORGANIZATION, "USER", SCIM_WRITE)).thenReturn(List.of("u-1"));

        assertThat(adapter.userIdsWith(ORGANIZATION, SCIM_WRITE)).containsExactly("u-1");
    }

    @Test
    void findsTheUsersOfAGroupBoundToThePermission() {

        when(bindings.subjectsHolding(ORGANIZATION, "GROUP", SCIM_WRITE)).thenReturn(List.of("platform-admins"));
        when(directoryGroups.userIdsInGroups(ORGANIZATION, List.of("platform-admins"))).thenReturn(List.of("u-2"));

        assertThat(adapter.userIdsWith(ORGANIZATION, SCIM_WRITE)).containsExactly("u-2");
    }

    @Test
    void namesSomebodyOnlyOnceWhenTheyHoldItBothDirectlyAndThroughAGroup() {

        when(bindings.subjectsHolding(ORGANIZATION, "USER", SCIM_WRITE)).thenReturn(List.of("u-1"));
        when(bindings.subjectsHolding(ORGANIZATION, "GROUP", SCIM_WRITE)).thenReturn(List.of("platform-admins"));
        when(directoryGroups.userIdsInGroups(ORGANIZATION, List.of("platform-admins"))).thenReturn(List.of("u-1"));

        assertThat(adapter.userIdsWith(ORGANIZATION, SCIM_WRITE)).containsExactly("u-1");
    }

    @Test
    void answersNobodyForACodeNoPermissionCarries() {

        assertThat(adapter.userIdsWith(ORGANIZATION, "not:a-permission")).isEmpty();
        verifyNoInteractions(bindings, directoryGroups);
    }

    @Test
    void answersNobodyForARetiredCodeARoleMayStillCarry() {

        assertThat(adapter.userIdsWith(ORGANIZATION, new Permission("idp_connection:write").code())).isEmpty();
        verifyNoInteractions(bindings, directoryGroups);
    }
}
