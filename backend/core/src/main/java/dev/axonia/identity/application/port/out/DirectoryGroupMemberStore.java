package dev.axonia.identity.application.port.out;

import java.util.Collection;
import java.util.List;

import dev.axonia.identity.domain.DirectoryGroupMember;

/** Who is in which synced directory group, by SCIM user id. */
public interface DirectoryGroupMemberStore {

    List<DirectoryGroupMember> findByOrganizationIdAndGroupId(String organizationId, Long groupId);

    /** The distinct signed-in users among the members of the given groups. */
    List<String> findUserIdsByOrganizationIdAndGroupIdIn(String organizationId, Collection<Long> groupIds);

    /** Adding a member twice keeps the one membership. */
    void insertIgnoreDuplicate(String organizationId, Long groupId, String userScimId);

    void deleteByOrganizationIdAndGroupIdAndUserScimId(String organizationId, Long groupId, String userScimId);

    void deleteAllByOrganizationIdAndGroupId(String organizationId, Long groupId);
}
