package dev.axonia.learning.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.learning.api.HeldArea;
import dev.axonia.learning.api.Learner;
import dev.axonia.learning.api.LearningIntents;
import dev.axonia.learning.api.LearningPage;
import dev.axonia.learning.application.port.out.CommunityAccess;
import dev.axonia.learning.application.port.out.CurrentUser;
import dev.axonia.learning.application.port.out.LearningIntentRepository;
import dev.axonia.learning.application.port.out.OrganizationContext;
import dev.axonia.learning.application.port.out.PersonDirectory;
import dev.axonia.learning.application.port.out.Vocabulary;
import dev.axonia.learning.domain.LearningFold;
import dev.axonia.learning.domain.LearningIntent;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * The areas members want to learn, one community at a time. Intents are stored as typed and read through the
 * community's merges. The caller is always the learner, so nobody adds for anybody else.
 */
@Service
@RequiredArgsConstructor
public class LearningService implements LearningIntents {

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final LearningIntentRepository repository;
    private final Vocabulary vocabulary;
    private final PersonDirectory personDirectory;
    private final Clock clock;

    /**
     * Adds the area to the caller's, stored as typed. Adding an area already held, under any spelling the community
     * reads as it, returns the first intent. The cap counts areas, not spellings. An archived community takes no new
     * intent.
     */
    @Override
    @Transactional
    public HeldArea add(String slug, String rawTag) {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();
        var community = communityAccess.communityForSlug(slug);
        if (community.archived()) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot add to an archived community");
        }
        lockAsMember(org, community.id(), userId);
        var tag = vocabulary.writableName(rawTag);
        var areas = vocabulary.areasOf(community.id());
        var area = areas.canonicalOf(tag);
        var held = LearningFold.held(repository.inCommunity(org, community.id(), userId), areas);
        var existing = held.stream().filter(h -> h.tag().equals(area)).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        if (held.size() >= INTENT_LIMIT) {
            throw new ConflictException("LEARNING_INTENT_LIMIT",
                    "At most " + INTENT_LIMIT + " areas to learn per community");
        }
        var declaredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        repository.insert(org, LearningIntent.builder().communityId(community.id()).userId(userId).tag(tag)
                .declaredAt(declaredAt).build());
        return new HeldArea(area, declaredAt);
    }

    /**
     * Removes the area from the caller's, under every spelling they held it by, and answers the area as the community
     * reads it. Still open in an archived community, so nobody is stuck with a statement they no longer make.
     */
    @Override
    @Transactional
    public String remove(String slug, String rawTag) {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();
        var communityId = communityAccess.communityForSlug(slug).id();
        lockAsMember(org, communityId, userId);
        var areas = vocabulary.areasOf(communityId);
        var area = areas.canonicalOf(TagNormalizer.normalize(rawTag));
        repository.delete(org, communityId, userId, areas.variantsOf(area));
        return area;
    }

    /** The caller's areas when they are a member, and what the community's current members want, for anyone. */
    @Override
    @Transactional(readOnly = true)
    public LearningPage page(String slug) {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();
        var community = communityAccess.communityForSlug(slug);
        var areas = vocabulary.areasOf(community.id());
        var member = communityAccess.isMember(community.id(), userId);
        var mine = member
                ? LearningFold.held(repository.inCommunity(org, community.id(), userId), areas)
                : List.<HeldArea>of();
        var wanted = LearningFold.wanted(repository.currentIn(org, community.id()), areas, vocabulary.canBeAsked(slug));
        return new LearningPage(member, community.archived(), INTENT_LIMIT, mine, wanted);
    }

    /** The current members who want the area the tag names in this community, earliest first. */
    @Override
    @Transactional(readOnly = true)
    public List<Learner> learners(String slug, String rawTag) {

        var org = organizationContext.organizationId();
        var communityId = communityAccess.communityForSlug(slug).id();
        var areas = vocabulary.areasOf(communityId);
        var area = areas.canonicalOf(TagNormalizer.normalize(rawTag));
        var firsts = LearningFold.learners(repository.currentIn(org, communityId), areas, area);
        var people = personDirectory.resolve(firsts.stream().map(Map.Entry::getKey).toList());
        return firsts.stream().map(first -> {
            var person = people.get(first.getKey());
            return new Learner(first.getKey(), person.displayName(), person.avatarUrl(), first.getValue());
        }).toList();
    }

    /**
     * Deletes a departed member's intents in the community they left, under the locks an add takes. The organization is
     * the caller's: a departure can arrive on a path that carries no member token.
     */
    @Transactional
    public void forgetDepartedLearner(String organizationId, long communityId, String userId) {

        vocabulary.shareLock(organizationId, communityId);
        repository.lockLearner(organizationId, communityId, userId);
        repository.deleteAllIn(organizationId, communityId, userId);
    }

    /**
     * The vocabulary lock first, so no curator rewrites the names this call canonicalizes against, then the learner's,
     * so parallel adds cannot all pass the cap and an add racing a leave cannot strand its row. Membership is read
     * under both.
     */
    private void lockAsMember(String org, long communityId, String userId) {

        vocabulary.shareLock(org, communityId);
        repository.lockLearner(org, communityId, userId);
        if (!communityAccess.isMember(communityId, userId)) {
            throw new ForbiddenException("Community membership required");
        }
    }
}
