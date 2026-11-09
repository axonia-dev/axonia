package dev.axonia.events.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.events.api.ProposeTopicRequest;
import dev.axonia.events.api.SpeakerAssignment;
import dev.axonia.events.api.Topic;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicQuery;
import dev.axonia.events.api.TopicRemoved;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.api.Topics;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.application.port.out.TopicVoteStore;
import dev.axonia.events.domain.Descriptions;
import dev.axonia.events.domain.Tags;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicTag;
import dev.axonia.events.domain.TopicVoteRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopicService implements Topics {

    private final TopicStore topicStore;
    private final TopicVoteStore topicVoteStore;
    private final TopicAuthorizer topicAuthorizer;
    private final SlotService slotService;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final TopicEvidence topicEvidence;
    private final TopicReadModel topicReadModel;
    private final TopicMemberResolver topicMemberResolver;

    @Override
    @Transactional
    public Topic propose(String slug, ProposeTopicRequest request) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        requireMember(communityId);
        var proposerUserId = topicMemberResolver.resolveProposer(communityId, currentUser.userId(), request.proposer());

        var cleanedTags = Tags.clean(request.tags());
        var speakers = request.kind() == TopicKind.OFFER ? List.of(proposerUserId) : List.<String>of();
        var saved = topicStore.save(
                TopicRecord.builder().organizationId(organizationId).communityId(communityId).title(request.title())
                        .description(Descriptions.orEmpty(request.description())).proposerUserId(proposerUserId)
                        .status(TopicStatus.PROPOSED).tags(tagRefs(cleanedTags)).build().withSpeakers(speakers));
        topicEvidence.publish(saved, speakers);

        return topicReadModel.single(saved, null);
    }

    @Transactional(readOnly = true)
    public Topic get(Long id) {

        return topicReadModel.single(findOrThrow(organizationContext.organizationId(), id), null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Topic> list(String slug, TopicQuery query, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return topicReadModel.list(TopicListQuery.builder().organizationId(organizationId).communityId(communityId)
                .kind(query.kind()).status(query.status()).build(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Topic> listPast(String slug, String q, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return topicReadModel.listPast(organizationId, communityId, q, pageable);
    }

    @Transactional(readOnly = true)
    public TopicCounts counts(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return topicStore.countByLifecycle(organizationId, communityId);
    }

    @Transactional
    public Topic update(Long id, UpdateTopicRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        topicAuthorizer.requireTopicManagementPermission(entity.getCommunityId(), currentUser.userId());
        var cleanedTags = Tags.clean(request.tags());
        var saved = topicStore.save(entity.toBuilder().title(request.title())
                .description(Descriptions.orEmpty(request.description())).tags(tagRefs(cleanedTags)).build());
        topicEvidence.publish(saved, topicReadModel.speakersOf(organizationId, List.of(saved)).get(id));
        return topicReadModel.single(saved, null);
    }

    @Transactional
    public void returnToBacklog(Long id) {

        var organizationId = organizationContext.organizationId();
        var schedule = topicStore.findSchedules(organizationId, List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ConflictException("NOT_SCHEDULED", "Topic is not scheduled in any session"));
        slotService.removeSlot(schedule.sessionId(), schedule.sectionId(), schedule.slotId());
    }

    @Transactional
    public void remove(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        topicAuthorizer.requireTopicManagementPermission(entity.getCommunityId(), currentUser.userId());
        if (entity.getStatus() != TopicStatus.PROPOSED) {
            throw new ConflictException("NOT_PROPOSED", "Only proposed topics may be deleted");
        }
        topicStore.delete(entity);
        eventPublisher.publishEvent(new TopicRemoved(entity.getId(), entity.getCommunityId()));
    }

    @Transactional
    public Topic vote(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        requireMember(entity.getCommunityId());
        try {
            topicVoteStore.save(TopicVoteRecord.builder().organizationId(organizationId).topicId(id)
                    .userId(currentUser.userId()).build());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("ALREADY_VOTED", "You have already voted for this topic");
        }
        return topicReadModel.single(entity, null);
    }

    @Transactional
    public void unvote(Long id) {

        var organizationId = organizationContext.organizationId();
        var vote = topicVoteStore.findByOrganizationIdAndTopicIdAndUserId(organizationId, id, currentUser.userId())
                .orElseThrow(() -> new NotFoundException("Vote not found"));
        topicVoteStore.delete(vote);
    }

    /** Any member may volunteer to present a backlog topic. Volunteering twice changes nothing. */
    @Transactional
    public Topic addSelfAsSpeaker(Long id) {

        var topic = findBacklogTopicForUpdate(id);
        var userId = currentUser.userId();
        requireMember(topic.getCommunityId());
        return topicReadModel.single(topic.hasSpeaker(userId) ? topic : saveSpeakers(topic.withSpeaker(userId)), null);
    }

    /** Any speaker may step back. The last one leaving turns the offer back into a request in its creator's name. */
    @Transactional
    public Topic removeSelfAsSpeaker(Long id) {

        var topic = findBacklogTopicForUpdate(id);
        var userId = currentUser.userId();
        return topicReadModel.single(topic.hasSpeaker(userId) ? saveSpeakers(topic.withoutSpeaker(userId)) : topic,
                null);
    }

    /**
     * Names somebody else as a speaker, resolved like a slot's presenter: a reference matching nobody or several leaves
     * the topic as it was and says why, rather than guessing. Naming a current speaker again changes nothing.
     */
    @Transactional
    public SpeakerAssignment addSpeaker(Long id, String reference) {

        var topic = findBacklogTopicForUpdate(id);
        topicAuthorizer.requireSpeakerManager(topic, currentUser.userId());
        return switch (communityAccess.resolvePerson(topic.getCommunityId(), reference.strip())) {
            case MemberMatch.Matched matched -> new SpeakerAssignment(topicReadModel.single(
                    topic.hasSpeaker(matched.userId()) ? topic : saveSpeakers(topic.withSpeaker(matched.userId())),
                    null), true, null, 0);
            case MemberMatch.Ambiguous ambiguous -> new SpeakerAssignment(topicReadModel.single(topic, null), false,
                    SpeakerAssignment.AMBIGUOUS, ambiguous.candidates());
            case MemberMatch.NotFound ignored ->
                new SpeakerAssignment(topicReadModel.single(topic, null), false, SpeakerAssignment.NOT_FOUND, 0);
        };
    }

    @Transactional
    public Topic removeSpeaker(Long id, String userId) {

        var topic = findBacklogTopicForUpdate(id);
        topicAuthorizer.requireSpeakerManager(topic, currentUser.userId());
        return topicReadModel.single(topic.hasSpeaker(userId) ? saveSpeakers(topic.withoutSpeaker(userId)) : topic,
                null);
    }

    /** Speakers are the backlog's to change: once scheduled, a topic's speakers are its slot's presenters. */
    private TopicRecord findBacklogTopicForUpdate(Long id) {

        var topic = topicStore.findForUpdate(organizationContext.organizationId(), id)
                .orElseThrow(() -> new NotFoundException("Topic not found: " + id));
        if (topic.getStatus() != TopicStatus.PROPOSED) {
            throw new ConflictException("NOT_PROPOSED", "This topic is no longer on the backlog");
        }
        return topic;
    }

    private TopicRecord saveSpeakers(TopicRecord topic) {

        var saved = topicStore.save(topic);
        topicEvidence.publish(saved, saved.speakerUserIds());
        return saved;
    }

    private void requireMember(long communityId) {

        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
    }

    TopicRecord findOrThrow(String organizationId, Long id) {

        return topicStore.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Topic not found: " + id));
    }

    private Set<TopicTag> tagRefs(Set<String> tags) {

        return tags.stream().map(t -> TopicTag.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
