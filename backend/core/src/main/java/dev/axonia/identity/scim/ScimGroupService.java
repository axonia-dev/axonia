package dev.axonia.identity.scim;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;
import dev.axonia.identity.OrganizationIdpResolver;
import dev.axonia.identity.internal.DirectoryGroupEntity;
import dev.axonia.identity.internal.DirectoryGroupMemberRepository;
import dev.axonia.identity.internal.DirectoryGroupRepository;
import dev.axonia.identity.scim.DirectoryGroupHold.DirectoryGroupRekey;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScimGroupService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final Pattern EQ_FILTER = Pattern.compile("^(\\w+)\\s+eq\\s+\"([^\"]+)\"$",
            Pattern.CASE_INSENSITIVE);

    private final DirectoryGroupRepository groupRepository;
    private final DirectoryGroupMemberRepository memberRepository;
    private final ScimGroupMapper mapper;
    private final OrganizationIdpResolver resolver;
    private final DirectoryGroupHold hold;

    @Transactional
    public ScimGroup create(ScimPrincipal p, ScimGroup in) {

        String scimId = UUID.randomUUID().toString();
        String reconciliationKey = reconciliationKeyFor(p, in);

        groupRepository.findByOrganizationIdAndReconciliationKey(p.organizationId(), reconciliationKey)
                .ifPresent(existing -> {
                    throw ScimException.uniquenessConflict("Group already exists: " + reconciliationKey);
                });

        DirectoryGroupEntity entity = DirectoryGroupEntity.builder().organizationId(p.organizationId()).scimId(scimId)
                .externalId(in.externalId()).displayName(in.displayName()).reconciliationKey(reconciliationKey)
                .confirmedAt(hold.stampFor(p.organizationId(), reconciliationKey)).build();

        DirectoryGroupEntity saved = saveReconciledBy(entity, reconciliationKey);

        if (in.members() != null) {
            for (ScimGroupMember member : in.members()) {
                memberRepository.insertIgnoreDuplicate(p.organizationId(), saved.getId(), member.value());
            }
        }

        return mapper.toScim(saved, loadMembers(p.organizationId(), saved.getId()));
    }

    @Transactional(readOnly = true)
    public ScimGroup get(ScimPrincipal p, String scimId) {

        DirectoryGroupEntity entity = groupRepository.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM group found: " + scimId));

        return mapper.toScim(entity, loadMembers(p.organizationId(), entity.getId()));
    }

    @Transactional(readOnly = true)
    public ScimListResponse<ScimGroup> list(ScimPrincipal p, String filter) {

        List<DirectoryGroupEntity> groups;

        if (filter == null || filter.isBlank()) {
            groups = groupRepository.findByOrganizationId(p.organizationId(), PageRequest.of(0, DEFAULT_PAGE_SIZE));
        } else {
            groups = applyFilter(p.organizationId(), filter);
        }

        List<ScimGroup> scimGroups = groups.stream()
                .map(g -> mapper.toScim(g, loadMembers(p.organizationId(), g.getId()))).toList();

        return ScimListResponse.of(scimGroups.size(), 1, scimGroups.size(), scimGroups);
    }

    @Transactional
    public ScimGroup replace(ScimPrincipal p, String scimId, ScimGroup in) {

        DirectoryGroupEntity existing = groupRepository.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM group found: " + scimId));

        DirectoryGroupEntity updated = existing.toBuilder().displayName(in.displayName()).externalId(in.externalId())
                .build();

        DirectoryGroupEntity saved = groupRepository.save(updated);

        memberRepository.deleteAllByOrganizationIdAndGroupId(p.organizationId(), saved.getId());

        if (in.members() != null) {
            for (ScimGroupMember member : in.members()) {
                memberRepository.insertIgnoreDuplicate(p.organizationId(), saved.getId(), member.value());
            }
        }

        return mapper.toScim(saved, loadMembers(p.organizationId(), saved.getId()));
    }

    @Transactional
    public ScimGroup patch(ScimPrincipal p, String scimId, ScimPatchRequest patch) {

        DirectoryGroupEntity existing = groupRepository.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM group found: " + scimId));

        for (ScimPatchOperation op : patch.operations()) {
            if ("members".equalsIgnoreCase(op.path())) {
                handleMembersPatch(p, existing, op);
            } else if ("displayName".equalsIgnoreCase(op.path()) && "replace".equalsIgnoreCase(op.op())) {
                String newName = op.value() != null ? op.value().toString() : existing.getDisplayName();
                // ENTRA uses the external object-id (externalId) as the reconciliation key, so a displayName
                // rename must not change it. Keycloak derives the key from displayName at create time, so there a
                // rename must keep them in sync.
                String newKey = isEntra(p.organizationId()) ? existing.getReconciliationKey() : newName;
                Instant confirmedAt = hold.afterRekey(p.organizationId(),
                        new DirectoryGroupRekey(existing.getReconciliationKey(), existing.getConfirmedAt(), newKey));
                existing = saveReconciledBy(existing.toBuilder().displayName(newName).reconciliationKey(newKey)
                        .confirmedAt(confirmedAt).build(), newKey);
            }
            // Other op/path combinations are not supported; known ops above are applied, rest is skipped.
        }

        return mapper.toScim(existing, loadMembers(p.organizationId(), existing.getId()));
    }

    private void handleMembersPatch(ScimPrincipal p, DirectoryGroupEntity existing, ScimPatchOperation op) {

        List<String> memberValues = extractMemberValues(op.value());
        if ("add".equalsIgnoreCase(op.op())) {
            for (String userScimId : memberValues) {
                memberRepository.insertIgnoreDuplicate(p.organizationId(), existing.getId(), userScimId);
            }
        } else if ("remove".equalsIgnoreCase(op.op())) {
            for (String userScimId : memberValues) {
                memberRepository.deleteByOrganizationIdAndGroupIdAndUserScimId(p.organizationId(), existing.getId(),
                        userScimId);
            }
        }
    }

    public void delete(ScimPrincipal p, String scimId) {

        DirectoryGroupEntity existing = groupRepository.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM group found: " + scimId));

        groupRepository.delete(existing);
    }

    // --- private helpers ---

    /**
     * The key a group is reconciled by, never null whichever arm produced it. Entra reconciles by the directory object
     * id that arrives as {@code externalId}, everyone else by {@code displayName}. A push that omits its arm's
     * attribute leaves nothing to reconcile by, and {@code directory_group.reconciliation_key} is {@code NOT NULL}, so
     * accepting it would answer the provisioning job with a 500 rather than telling its administrator what is missing.
     */
    private String reconciliationKeyFor(ScimPrincipal p, ScimGroup in) {

        if (isEntra(p.organizationId())) {
            return requireKey(in.externalId(),
                    "This account provisions from Microsoft Entra, where a group is matched by its directory object "
                            + "id. This request carried no externalId, so the group could not be matched to the roles "
                            + "bound to it. Enable the externalId attribute mapping on the provisioning app.");
        }
        return requireKey(in.displayName(),
                "A group is matched by its displayName in this account, and RFC 7643 4.2 requires one of every SCIM "
                        + "group. This request carried none, so the group could not be matched to the roles bound "
                        + "to it.");
    }

    private String requireKey(String reconciliationKey, String detail) {

        if (reconciliationKey == null) {
            throw ScimException.badRequest(detail);
        }
        return reconciliationKey;
    }

    /**
     * {@code (organization_id, reconciliation_key)} is unique in the database (V41), and that constraint - not the read
     * before it - is what makes the conflict true. Two provisioning calls racing each other both see no existing group,
     * and the one that loses must still answer the RFC 7644 3.3 uniqueness conflict the caller can recover from, never
     * a 500.
     */
    private DirectoryGroupEntity saveReconciledBy(DirectoryGroupEntity entity, String reconciliationKey) {

        try {
            return groupRepository.save(entity);
        } catch (DuplicateKeyException e) {
            throw ScimException.uniquenessConflict("Group already exists: " + reconciliationKey);
        }
    }

    private boolean isEntra(String organizationId) {

        return resolver.resolve(organizationId).map(OrganizationIdp::provider).orElse(IdpProvider.GENERIC_OIDC)
                .federatesMicrosoftDirectory();
    }

    private List<DirectoryGroupEntity> applyFilter(String organizationId, String filter) {

        Matcher m = EQ_FILTER.matcher(filter.trim());
        if (!m.matches()) {
            return List.of();
        }

        String attr = m.group(1);
        String value = m.group(2);

        if ("displayName".equalsIgnoreCase(attr)) {
            return groupRepository.findByOrganizationIdAndDisplayName(organizationId, value);
        }

        return List.of();
    }

    private List<ScimGroupMember> loadMembers(String organizationId, Long groupId) {

        return memberRepository.findByOrganizationIdAndGroupId(organizationId, groupId).stream()
                .map(m -> new ScimGroupMember(m.getUserScimId(), null)).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractMemberValues(Object value) {

        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item instanceof Map).map(item -> (Map<String, Object>) item)
                    .map(map -> (String) map.get("value")).filter(v -> v != null).toList();
        }
        return List.of();
    }
}
