package dev.axonia.identity.scim;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.internal.DirectoryGroupEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ScimGroupMapperTest {

    private final ScimGroupMapper mapper = new ScimGroupMapper();

    @Test
    void toScim_mapsEntityFieldsToScimGroup() {

        // given
        DirectoryGroupEntity entity = DirectoryGroupEntity.builder().scimId("sg-abc").externalId("ext-abc")
                .displayName("Engineering").organizationId("default").build();
        List<ScimGroupMember> members = List.of(new ScimGroupMember("user-scim-1", null));

        // when
        ScimGroup result = mapper.toScim(entity, members);

        // then
        assertThat(result.id()).isEqualTo("sg-abc");
        assertThat(result.externalId()).isEqualTo("ext-abc");
        assertThat(result.displayName()).isEqualTo("Engineering");
        assertThat(result.members()).isEqualTo(members);
        assertThat(result.meta().resourceType()).isEqualTo("Group");
    }
}
