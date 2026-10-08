package dev.axonia.community;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.internal.CommunityKindEntity;
import dev.axonia.community.internal.CommunityKindRepository;
import lombok.RequiredArgsConstructor;

/**
 * Seeds a fresh organization's community-kind vocabulary — the same defaults V1 seeded for the {@code default} org.
 * Called once from hub provisioning, inside the provisioning transaction, under the new hub's RLS scope. Without this a
 * fresh hub cannot create its first community (creation requires a kindId).
 */
@Service
@RequiredArgsConstructor
public class CommunityKindDefaults {

    private static final List<String> DEFAULT_KINDS = List.of("Community of Practice");

    private final CommunityKindRepository repository;

    @Transactional
    public void seedFor(String organizationId) {
        DEFAULT_KINDS.forEach(label -> repository
                .save(CommunityKindEntity.builder().organizationId(organizationId).label(label).build()));
    }
}
