package dev.axonia.community;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.Permission;
import dev.axonia.community.internal.CommunityKindEntity;
import dev.axonia.community.internal.CommunityKindRepository;
import dev.axonia.community.internal.CommunityRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommunityKindService {

    private final CommunityKindRepository communityKindRepository;
    private final CommunityRepository communityRepository;
    private final OrganizationContext organizationContext;
    private final AuthorizationGate gate;

    @Transactional(readOnly = true)
    public List<CommunityKind> list() {

        // Reading the kind list is reference data every community-edit flow needs; it is not gated by the admin
        // COMMUNITY_KIND_READ permission. Mutations below remain gated by COMMUNITY_KIND_WRITE.
        var organizationId = organizationContext.organizationId();
        var counts = communityRepository.countCommunitiesByKind(organizationId).stream()
                .collect(Collectors.toMap(CommunityRepository.KindCommunityCount::kindId,
                        CommunityRepository.KindCommunityCount::communityCount));
        return communityKindRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream()
                .map(e -> toKind(e, counts.getOrDefault(e.getId(), 0L))).toList();
    }

    @Transactional
    public CommunityKind create(CreateKindRequest request) {

        gate.requirePermission(Permission.COMMUNITY_KIND_WRITE);
        var organizationId = organizationContext.organizationId();
        if (communityKindRepository.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("KIND_EXISTS", "Kind already exists: " + request.label());
        }
        var saved = communityKindRepository
                .save(CommunityKindEntity.builder().organizationId(organizationId).label(request.label()).build());
        return toKind(saved, 0L);
    }

    @Transactional
    public CommunityKind rename(Long kindId, RenameKindRequest request) {

        gate.requirePermission(Permission.COMMUNITY_KIND_WRITE);
        var organizationId = organizationContext.organizationId();
        var kind = requireKind(organizationId, kindId);
        var label = request.label();
        if (!label.equals(kind.getLabel())
                && communityKindRepository.existsByOrganizationIdAndLabel(organizationId, label)) {
            throw new ConflictException("KIND_EXISTS", "Kind already exists: " + label);
        }
        var saved = communityKindRepository.save(kind.toBuilder().label(label).build());
        return toKind(saved, communityRepository.countByOrganizationIdAndKindId(organizationId, kindId));
    }

    @Transactional
    public void delete(Long kindId, Long reassignToId) {

        gate.requirePermission(Permission.COMMUNITY_KIND_WRITE);
        var organizationId = organizationContext.organizationId();
        var kind = requireKind(organizationId, kindId);

        if (communityRepository.countByOrganizationIdAndKindId(organizationId, kindId) > 0) {
            if (reassignToId == null) {
                throw new ConflictException("KIND_IN_USE",
                        "Kind is in use; a reassignment target is required to delete it");
            }
            if (reassignToId.equals(kindId)) {
                throw new ConflictException("INVALID_REASSIGNMENT", "Cannot reassign communities to the deleted kind");
            }
            requireKind(organizationId, reassignToId);
            communityRepository.reassignKind(organizationId, kindId, reassignToId);
        }

        communityKindRepository.delete(kind);
    }

    private CommunityKindEntity requireKind(String organizationId, Long kindId) {

        return communityKindRepository.findByOrganizationIdAndId(organizationId, kindId)
                .orElseThrow(() -> new NotFoundException("Kind not found: " + kindId));
    }

    private CommunityKind toKind(CommunityKindEntity e, long communityCount) {

        return CommunityKind.builder().id(e.getId()).label(e.getLabel()).communityCount(communityCount).build();
    }
}
