package dev.axonia.identity.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.Pageable;

import dev.axonia.identity.IdentityProviderDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@MockitoSettings
class SyncedDirectoryGroupSourceTest {

    @Mock
    private DirectoryGroupRepository repository;

    @InjectMocks
    private SyncedDirectoryGroupSource sut;

    @Test
    void listGroups_keysByReconciliationKey_andDropsNullKeys() {
        var g1 = DirectoryGroupEntity.builder().organizationId("acme").reconciliationKey("b7c1").displayName("Platform")
                .build();
        var g2 = DirectoryGroupEntity.builder().organizationId("acme").reconciliationKey(null).displayName("Stranded")
                .build();
        when(repository.findByOrganizationId(eq("acme"), any(Pageable.class))).thenReturn(List.of(g1, g2));

        var groups = sut.listGroups("acme");

        assertThat(groups).extracting(IdentityProviderDirectory.IdpGroup::groupId).containsExactly("b7c1");
        assertThat(groups).extracting(IdentityProviderDirectory.IdpGroup::displayName).containsExactly("Platform");
    }
}
