package dev.axonia.identity.scim;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.internal.DirectoryGroupEntity;

@Component
public class ScimGroupMapper {

    public ScimGroup toScim(DirectoryGroupEntity entity, List<ScimGroupMember> members) {

        ScimMeta meta = new ScimMeta("Group", null, null, null);
        return ScimGroup.builder().id(entity.getScimId()).externalId(entity.getExternalId())
                .displayName(entity.getDisplayName()).members(members).meta(meta).build();
    }
}
