package dev.axonia.knowledge.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.ResourceLink;
import dev.axonia.events.SessionResourceAccess;
import dev.axonia.events.SessionResources;
import lombok.RequiredArgsConstructor;

/**
 * Adapter that satisfies the {@link SessionResourceAccess} port (declared in {@code events}) by resolving a session's
 * attached resources - already a single query via {@link SessionResourceRepository} - to each one's artifact title and
 * URL, routing session-level attachments ({@code segmentId == null}) into {@link SessionResources#sessionLevel} and the
 * rest into {@link SessionResources#bySlot}.
 *
 * <p>
 * Lives in {@code knowledge.internal} - {@code knowledge} is the module that owns artifacts and attachments, and
 * already depends on {@code events} (see {@code SessionAccessAdapter}), so an adapter here adds no new inter-module
 * edge. Declaring this port the other way round - {@code events} importing a {@code knowledge} read port - would create
 * a cycle {@code ApplicationModules.verify()} rejects.
 *
 * <p>
 * Unlike {@link dev.axonia.knowledge.ResourceService#resourcesForSession}, this bypasses membership authorization: the
 * caller is another bounded context's internal composer, already trusted with the session's own data, not a
 * member-facing request.
 *
 * <p>
 * {@code organizationId} is trusted from the caller, the same as every other org-explicit seam this codebase uses for a
 * no-JWT caller ({@code CommunityAccess.communicationSettings(String, long)}, {@code
 * MessageSubjectPolicyAdapter}). That trust is not RLS alone: both queries this adapter runs -
 * {@link SessionResourceRepository#findForSession}'s {@code att.organization_id = :organizationId} predicate and
 * {@link ArtifactRepository#findByOrganizationIdAndId}'s derived {@code WHERE organization_id = ? AND id = ?} - filter
 * by organization at the SQL predicate level, so a caller that passes the wrong organization id for a real session in a
 * different one gets an empty result, never that session's data. Each predicate guards a distinct leak path - an
 * attachment row can be mismatched against its artifact's real owner in either direction - so
 * {@code bothOrganizationPredicatesIndependentlyKeepTheWrongTenantsResourceOut} in the test class seeds both mismatches
 * and was verified, by temporarily dropping each predicate in turn, to fail on exactly the one it targets: it runs
 * against the schema owner connection, which bypasses RLS by ownership - the one connection where a dropped predicate
 * would otherwise go completely unnoticed.
 *
 * <p>
 * {@code communityId} is trusted the same way: the caller ({@code SessionFactsAssembler}) already read the session
 * entity and passes its own {@code communityId}, so {@link SessionResourceRepository#findForSession} can constrain the
 * query to it. Before this predicate existed, a resource attached under a session id that named a different community
 * would still render in this session's wrap-up mail, the same gap {@code ResourceService.resourcesForSession} closed
 * for the member-facing session page.
 */
@Component
@RequiredArgsConstructor
class SessionResourceAccessAdapter implements SessionResourceAccess {

    private final SessionResourceRepository sessionResourceRepository;
    private final ArtifactRepository artifactRepository;

    @Override
    @Transactional(readOnly = true)
    public SessionResources resourcesForSession(String organizationId, long sessionId, long communityId) {

        var rows = sessionResourceRepository.findForSession(organizationId, sessionId, communityId);
        var sessionLevel = new ArrayList<ResourceLink>();
        var bySlot = new LinkedHashMap<Long, List<ResourceLink>>();
        for (var row : rows) {
            addResource(organizationId, row, sessionLevel, bySlot);
        }
        return new SessionResources(sessionLevel, bySlot);
    }

    private void addResource(String organizationId, SessionResourceRow row, List<ResourceLink> sessionLevel,
            Map<Long, List<ResourceLink>> bySlot) {

        var link = artifactRepository.findByOrganizationIdAndId(organizationId, row.artifactId())
                .map(SessionResourceAccessAdapter::toResourceLink).orElse(null);
        if (link == null) {
            return;
        }
        var target = row.segmentId() == null
                ? sessionLevel
                : bySlot.computeIfAbsent(row.segmentId(), key -> new ArrayList<>());
        target.add(link);
    }

    private static ResourceLink toResourceLink(ArtifactEntity artifact) {

        var url = artifact.getUrl();
        return url.isBlank() ? null : new ResourceLink(artifact.getTitle(), url);
    }
}
