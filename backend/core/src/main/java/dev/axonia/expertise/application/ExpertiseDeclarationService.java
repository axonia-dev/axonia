package dev.axonia.expertise.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.application.port.out.VocabularyLock;
import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.TagCurationRules;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * A member's own declarations: the areas they are open to questions on, in one community. The caller is always the
 * declarer, so nobody can declare for anybody else.
 */
@Service
@RequiredArgsConstructor
public class ExpertiseDeclarationService {

    /** Keeps a profile from becoming a keyword list. */
    static final int DECLARATION_LIMIT = 20;

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final DeclarationRepository declarationRepository;
    private final TagAliasRepository tagAliasRepository;
    private final Clock clock;
    private final VocabularyLock vocabularyLock;

    /**
     * Declares the caller open to questions on the tag, stored as typed. Declaring the area again, under any spelling
     * the community's aliases read as it, returns the first declaration. The cap counts areas, not spellings, and the
     * result names the area as the member's own page does. An archived community takes no new declaration.
     */
    @Transactional
    public Declaration declare(String slug, String rawTag) {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();
        var community = communityAccess.communityForSlug(slug);
        if (community.archived()) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot declare in an archived community");
        }
        var communityId = lockMembership(org, community.id(), userId);
        var tag = TagCurationRules.requireWritableName(rawTag);

        var aliases = tagAliasRepository.aliasMap(org, communityId);
        var firstByArea = new HashMap<String, Instant>();
        for (var declaration : declarationRepository.inCommunity(org, communityId, userId)) {
            firstByArea.putIfAbsent(AliasResolver.canonicalOf(declaration.tag(), aliases), declaration.declaredAt());
        }
        var area = AliasResolver.canonicalOf(tag, aliases);
        var declaredArea = Declaration.builder().communityId(communityId).userId(userId).tag(area);
        if (firstByArea.containsKey(area)) {
            return declaredArea.declaredAt(firstByArea.get(area)).build();
        }
        if (firstByArea.size() >= DECLARATION_LIMIT) {
            throw new ConflictException("DECLARATION_LIMIT",
                    "At most " + DECLARATION_LIMIT + " declared areas per community");
        }
        var declaredAt = clock.instant();
        declarationRepository.insert(org,
                Declaration.builder().communityId(communityId).userId(userId).tag(tag).declaredAt(declaredAt).build());
        return declaredArea.declaredAt(declaredAt).build();
    }

    /**
     * Removes the caller's declaration of the area, whichever of its spellings they declared it under: declarations are
     * stored as typed and read through the community's merges.
     */
    @Transactional
    public void remove(String slug, String rawTag) {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();
        var communityId = lockMembership(org, communityAccess.communityIdForSlug(slug), userId);
        var aliases = tagAliasRepository.aliasMap(org, communityId);
        var canonical = AliasResolver.canonicalOf(TagNormalizer.normalize(rawTag), aliases);

        declarationRepository.delete(org, communityId, userId, AliasResolver.variantsOf(canonical, aliases));
    }

    /** Deletes a departed member's declarations in the community they left, under the locks a declare takes. */
    @Transactional
    public void forgetDepartedDeclarer(String organizationId, long communityId, String userId) {

        vocabularyLock.share(organizationId, communityId);
        declarationRepository.lockDeclarer(organizationId, communityId, userId);
        declarationRepository.deleteAllIn(organizationId, communityId, userId);
    }

    /**
     * Takes the community's shared lock, which a curator's merge, split, rename or delete waits on, then the declarer's
     * lock before reading the membership: a leave deletes the declarations under the same locks, so a declare either
     * commits before that delete runs or sees the membership already gone.
     */
    private long lockMembership(String org, long communityId, String userId) {

        vocabularyLock.share(org, communityId);
        declarationRepository.lockDeclarer(org, communityId, userId);
        if (!communityAccess.isMember(communityId, userId)) {
            throw new ForbiddenException("Community membership required");
        }
        return communityId;
    }
}
