package dev.axonia.community.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.community.domain.CommunityKindRecord;

public interface CommunityKindStore {

    List<CommunityKindRecord> listByLabel(String organizationId);

    Optional<CommunityKindRecord> find(String organizationId, Long id);

    boolean labelTaken(String organizationId, String label);

    CommunityKindRecord save(CommunityKindRecord kind);

    void delete(CommunityKindRecord kind);
}
