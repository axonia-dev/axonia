package dev.axonia.community.application;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityKind;
import dev.axonia.community.domain.CommunityKindRecord;
import dev.axonia.community.domain.KindCommunityCount;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommunityKindService {

    private final CommunityKindStore communityKindStore;
    private final CommunityStore communityStore;
    private final OrganizationContext organizationContext;
    private final AuthorizationGate gate;

    @Transactional(readOnly = true)
    public List<CommunityKind> list() {

        // Reading the kind list is reference data every community-edit flow needs; it is not gated by the admin
        // COMMUNITY_KIND_READ permission. Mutations below remain gated by COMMUNITY_KIND_WRITE.
        var organizationId = organizationContext.organizationId();
        var counts = communityStore.countByKind(organizationId).stream()
                .collect(Collectors.toMap(KindCommunityCount::kindId, KindCommunityCount::communityCount));
        return communityKindStore.listByLabel(organizationId).stream()
                .map(e -> toKind(e, counts.getOrDefault(e.getId(), 0L))).toList();
    }

    @Transactional
    public CommunityKind create(CreateKindRequest request) {

        gate.requirePermission(Permission.COMMUNITY_KIND_WRITE);
        var organizationId = organizationContext.organizationId();
        if (communityKindStore.labelTaken(organizationId, request.label())) {
            throw new ConflictException("KIND_EXISTS", "Kind already exists: " + request.label());
        }
        var saved = communityKindStore
                .save(CommunityKindRecord.builder().organizationId(organizationId).label(request.label()).build());
        return toKind(saved, 0L);
    }

    @Transactional
    public CommunityKind rename(Long kindId, RenameKindRequest request) {

        gate.requirePermission(Permission.COMMUNITY_KIND_WRITE);
        var organizationId = organizationContext.organizationId();
        var kind = requireKind(organizationId, kindId);
        var label = request.label();
        if (!label.equals(kind.getLabel()) && communityKindStore.labelTaken(organizationId, label)) {
            throw new ConflictException("KIND_EXISTS", "Kind already exists: " + label);
        }
        var saved = communityKindStore.save(kind.toBuilder().label(label).build());
        return toKind(saved, communityStore.countOfKind(organizationId, kindId));
    }

    @Transactional
    public void delete(Long kindId, Long reassignToId) {

        gate.requirePermission(Permission.COMMUNITY_KIND_WRITE);
        var organizationId = organizationContext.organizationId();
        var kind = requireKind(organizationId, kindId);

        if (communityStore.countOfKind(organizationId, kindId) > 0) {
            if (reassignToId == null) {
                throw new ConflictException("KIND_IN_USE",
                        "Kind is in use; a reassignment target is required to delete it");
            }
            if (reassignToId.equals(kindId)) {
                throw new ConflictException("INVALID_REASSIGNMENT", "Cannot reassign communities to the deleted kind");
            }
            requireKind(organizationId, reassignToId);
            communityStore.reassignKind(organizationId, kindId, reassignToId);
        }

        communityKindStore.delete(kind);
    }

    private CommunityKindRecord requireKind(String organizationId, Long kindId) {

        return communityKindStore.find(organizationId, kindId)
                .orElseThrow(() -> new NotFoundException("Kind not found: " + kindId));
    }

    private CommunityKind toKind(CommunityKindRecord e, long communityCount) {

        return CommunityKind.builder().id(e.getId()).label(e.getLabel()).communityCount(communityCount).build();
    }
}
