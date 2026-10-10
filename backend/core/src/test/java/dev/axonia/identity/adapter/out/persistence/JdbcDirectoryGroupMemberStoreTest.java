package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.identity.domain.DirectoryGroupMember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcDirectoryGroupMemberStoreTest {

    private static final DirectoryGroupMemberEntity ENTITY = DirectoryGroupMemberEntity.builder().id(10L)
            .organizationId("organizationId-1").groupId(12L).userScimId("userScimId-3")
            .createdAt(Instant.parse("2026-05-05T10:00:00Z")).updatedAt(Instant.parse("2026-06-06T10:00:00Z"))
            .version(16L).build();

    private static final DirectoryGroupMember DOMAIN = JdbcDirectoryGroupMemberStore.toDirectoryGroupMember(ENTITY);

    @Mock
    private DirectoryGroupMemberRepository directoryGroupMemberRepository;

    @InjectMocks
    private JdbcDirectoryGroupMemberStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcDirectoryGroupMemberStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByOrganizationIdAndGroupIdMapsEveryRowItFinds() {

        when(directoryGroupMemberRepository.findByOrganizationIdAndGroupId("organizationId-0", 11L))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndGroupId("organizationId-0", 11L)).containsExactly(DOMAIN);
    }

    @Test
    void findUserIdsByOrganizationIdAndGroupIdInAnswersTheRepositoryIds() {

        when(directoryGroupMemberRepository.findUserIdsByOrganizationIdAndGroupIdIn("organizationId-0",
                List.of(1L, 2L))).thenReturn(List.of("u-1"));

        assertThat(store.findUserIdsByOrganizationIdAndGroupIdIn("organizationId-0", List.of(1L, 2L)))
                .containsExactly("u-1");
    }

    @Test
    void insertIgnoreDuplicateDelegatesToTheRepository() {

        store.insertIgnoreDuplicate("organizationId-0", 11L, "userScimId-2");

        verify(directoryGroupMemberRepository).insertIgnoreDuplicate("organizationId-0", 11L, "userScimId-2");
    }

    @Test
    void deleteByOrganizationIdAndGroupIdAndUserScimIdDelegatesToTheRepository() {

        store.deleteByOrganizationIdAndGroupIdAndUserScimId("organizationId-0", 11L, "userScimId-2");

        verify(directoryGroupMemberRepository).deleteByOrganizationIdAndGroupIdAndUserScimId("organizationId-0", 11L,
                "userScimId-2");
    }

    @Test
    void deleteAllByOrganizationIdAndGroupIdDelegatesToTheRepository() {

        store.deleteAllByOrganizationIdAndGroupId("organizationId-0", 11L);

        verify(directoryGroupMemberRepository).deleteAllByOrganizationIdAndGroupId("organizationId-0", 11L);
    }
}
