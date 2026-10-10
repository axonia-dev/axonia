package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.AvatarSyncUpdate;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserSearch;
import dev.axonia.identity.domain.UserTimeZoneRow;
import dev.axonia.identity.domain.UserUpsert;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcUserStore implements UserStore {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByOrganizationIdAndUserId(String organizationId, String userId) {
        return userRepository.findByOrganizationIdAndUserId(organizationId, userId).map(JdbcUserStore::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> findByOrganizationIdAndUserIdIn(String organizationId, Collection<String> userIds) {
        return userRepository.findByOrganizationIdAndUserIdIn(organizationId, userIds).stream()
                .map(JdbcUserStore::toUserAccount).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> findByOrganizationId(String organizationId, int limit) {
        return userRepository.findByOrganizationId(organizationId, PageRequest.of(0, limit)).stream()
                .map(JdbcUserStore::toUserAccount).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByOrganizationIdAndScimId(String organizationId, String scimId) {
        return userRepository.findByOrganizationIdAndScimId(organizationId, scimId).map(JdbcUserStore::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByOrganizationIdAndExternalId(String organizationId, String externalId) {
        return userRepository.findByOrganizationIdAndExternalId(organizationId, externalId)
                .map(JdbcUserStore::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByOrganizationIdAndEmail(String organizationId, String email) {
        return userRepository.findByOrganizationIdAndEmail(organizationId, email).map(JdbcUserStore::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findPresentUserIds(String organizationId) {
        return userRepository.findPresentUserIds(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findActiveSublessByEmail(String organizationId, String email) {
        return userRepository.findActiveSublessByEmail(organizationId, email).map(JdbcUserStore::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findActiveByEmail(String organizationId, String email) {
        return userRepository.findActiveByEmail(organizationId, email).map(JdbcUserStore::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> search(UserSearch search) {
        return userRepository.search(search).stream().map(JdbcUserStore::toUserAccount).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTimeZoneRow> findTimeZones(String organizationId, Collection<String> userIds) {
        return userRepository.findTimeZones(organizationId, userIds);
    }

    @Override
    @Transactional
    public UserAccount save(UserAccount user) {
        return toUserAccount(userRepository.save(toEntity(user)));
    }

    @Override
    @Transactional
    public void attachSub(Long id, String userId, Instant seenAt) {
        userRepository.attachSub(id, userId, seenAt);
    }

    @Override
    @Transactional
    public void upsert(UserUpsert user, Instant seenAt) {
        userRepository.upsert(user, seenAt);
    }

    @Override
    @Transactional
    public void updateSyncedAvatar(AvatarSyncUpdate update) {
        userRepository.updateSyncedAvatar(update);
    }

    @Override
    @Transactional
    public void touchAvatarSyncedAt(String organizationId, String userId, Instant syncedAt) {
        userRepository.touchAvatarSyncedAt(organizationId, userId, syncedAt);
    }

    @Override
    @Transactional
    public void clearSyncedAvatar(String organizationId, String userId, Instant syncedAt) {
        userRepository.clearSyncedAvatar(organizationId, userId, syncedAt);
    }

    @Override
    @Transactional
    public void updateAvatarOverride(AvatarOverrideUpdate update, Instant updatedAt) {
        userRepository.updateAvatarOverride(update, updatedAt);
    }

    @Override
    @Transactional
    public void clearAvatarOverride(String organizationId, String userId, Instant updatedAt) {
        userRepository.clearAvatarOverride(organizationId, userId, updatedAt);
    }

    @Override
    @Transactional
    public void updateObservedTimeZone(String organizationId, String userId, String zone, Instant updatedAt) {
        userRepository.updateObservedTimeZone(organizationId, userId, zone, updatedAt);
    }

    @Override
    @Transactional
    public void updateDeclaredTimeZone(String organizationId, String userId, String zone, Instant updatedAt) {
        userRepository.updateDeclaredTimeZone(organizationId, userId, zone, updatedAt);
    }

    @Override
    @Transactional
    public void clearDeclaredTimeZone(String organizationId, String userId, Instant updatedAt) {
        userRepository.clearDeclaredTimeZone(organizationId, userId, updatedAt);
    }

    static UserAccount toUserAccount(UserEntity entity) {
        return UserAccount.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).displayName(entity.getDisplayName()).email(entity.getEmail())
                .active(entity.getActive()).externalId(entity.getExternalId()).scimId(entity.getScimId())
                .arrival(entity.getArrival()).avatarMediaKey(entity.getAvatarMediaKey())
                .avatarEtag(entity.getAvatarEtag()).avatarSyncedAt(entity.getAvatarSyncedAt())
                .avatarOverrideKey(entity.getAvatarOverrideKey()).lastSeenAt(entity.getLastSeenAt())
                .observedTimeZone(entity.getObservedTimeZone()).declaredTimeZone(entity.getDeclaredTimeZone())
                .businessUnit(entity.getBusinessUnit()).department(entity.getDepartment()).team(entity.getTeam())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static UserEntity toEntity(UserAccount userAccount) {
        return UserEntity.builder().id(userAccount.id()).organizationId(userAccount.organizationId())
                .userId(userAccount.userId()).displayName(userAccount.displayName()).email(userAccount.email())
                .active(userAccount.active()).externalId(userAccount.externalId()).scimId(userAccount.scimId())
                .arrival(userAccount.arrival()).avatarMediaKey(userAccount.avatarMediaKey())
                .avatarEtag(userAccount.avatarEtag()).avatarSyncedAt(userAccount.avatarSyncedAt())
                .avatarOverrideKey(userAccount.avatarOverrideKey()).lastSeenAt(userAccount.lastSeenAt())
                .observedTimeZone(userAccount.observedTimeZone()).declaredTimeZone(userAccount.declaredTimeZone())
                .businessUnit(userAccount.businessUnit()).department(userAccount.department()).team(userAccount.team())
                .createdAt(userAccount.createdAt()).updatedAt(userAccount.updatedAt()).version(userAccount.version())
                .build();
    }
}
