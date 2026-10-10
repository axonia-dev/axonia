package dev.axonia.identity.application.scim;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.domain.DirectoryGroup;

@Component
public class ScimGroupMapper {

    public ScimGroup toScim(DirectoryGroup entity, List<ScimGroupMember> members) {

        ScimMeta meta = new ScimMeta("Group", null, null, null);
        return ScimGroup.builder().id(entity.scimId()).externalId(entity.externalId()).displayName(entity.displayName())
                .members(members).meta(meta).build();
    }
}
