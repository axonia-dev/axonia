package dev.axonia.access.adapter.out.identity;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.access.adapter.out.persistence.JdbcRoleBindingStore;
import dev.axonia.access.adapter.out.persistence.JdbcRoleStore;
import dev.axonia.access.api.Permission;
import dev.axonia.access.application.KnownPermissions;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The adapter over the real store and Postgres in one composition, so the subject types it asks for are the ones a
 * binding is stored under.
 */
@Import({JdbcRoleStore.class, JdbcRoleBindingStore.class})
class PermissionHoldersAdapterJdbcTest extends CoreJdbcTestSupport {

    private final String organization = "org-" + UUID.randomUUID();
    private final DirectoryGroups directoryGroups = mock(DirectoryGroups.class);

    @Autowired
    private RoleStore roles;

    @Autowired
    private RoleBindingStore bindings;

    @Test
    void namesTheUserBoundDirectlyAndTheUsersOfTheGroupBound() {

        var role = roles.save(RoleDefinition.builder().organizationId(organization).name("Admins")
                .permissions(List.of(Permission.SCIM_WRITE.code())).build());
        bind("USER", "alice", role.id());
        bind("GROUP", "platform-admins", role.id());
        when(directoryGroups.userIdsInGroups(organization, List.of("platform-admins"))).thenReturn(List.of("bob"));
        var adapter = new PermissionHoldersAdapter(bindings, directoryGroups, new KnownPermissions(List.of()));

        var actual = adapter.userIdsWith(organization, Permission.SCIM_WRITE.code());

        assertThat(actual).isEqualTo(List.of("alice", "bob"));
    }

    private void bind(String subjectType, String subjectId, Long roleId) {

        bindings.save(SubjectBinding.builder().organizationId(organization).subjectType(subjectType)
                .subjectId(subjectId).subjectLabel(subjectId).roleId(roleId).build());
    }
}
