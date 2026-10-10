package dev.axonia.identity.application;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.IdentityProviderDirectory;
import dev.axonia.identity.application.port.out.DirectoryGroupStore;
import dev.axonia.identity.domain.DirectoryGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@MockitoSettings
class SyncedDirectoryGroupSourceTest {

    @Mock
    private DirectoryGroupStore repository;

    @InjectMocks
    private SyncedDirectoryGroupSource sut;

    @Test
    void listGroups_keysByReconciliationKey_andDropsNullKeys() {
        var g1 = DirectoryGroup.builder().organizationId("acme").reconciliationKey("b7c1").displayName("Platform")
                .build();
        var g2 = DirectoryGroup.builder().organizationId("acme").reconciliationKey(null).displayName("Stranded")
                .build();
        when(repository.findByOrganizationId(eq("acme"), anyInt())).thenReturn(List.of(g1, g2));

        var groups = sut.listGroups("acme");

        assertThat(groups).extracting(IdentityProviderDirectory.IdpGroup::groupId).containsExactly("b7c1");
        assertThat(groups).extracting(IdentityProviderDirectory.IdpGroup::displayName).containsExactly("Platform");
    }
}
