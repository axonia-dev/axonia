package dev.axonia.events.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.events.api.PresenterResolution;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.domain.ResolvedPresenters;
import lombok.RequiredArgsConstructor;

/**
 * Resolves the free-text presenter references a backfilled slot carries against a community's members, keeping the user
 * ids to assign and the caller's original query strings to report. Never guesses: a reference that does not resolve to
 * exactly one member of the community is reported, not assigned.
 *
 * <p>
 * The resolution itself belongs to {@code community}, which owns the roster, and is reached through this module's
 * anti-corruption port. A second copy of the rule here would be free to drift, and a drifted copy credits one person's
 * talk to another without anyone noticing: the very failure the rule exists to prevent.
 */
@Component
@RequiredArgsConstructor
public class PresenterResolver {

    private final CommunityAccess communityAccess;

    public ResolvedPresenters resolve(long communityId, List<String> queries) {

        if (queries.isEmpty()) {
            return new ResolvedPresenters(List.of(), PresenterResolution.empty());
        }
        var userIds = new ArrayList<String>();
        var assigned = new ArrayList<String>();
        var unmatched = new ArrayList<String>();
        var ambiguous = new ArrayList<String>();
        for (var query : queries) {
            if (query == null || query.isBlank()) {
                // An exported row whose speaker column was empty. There is no name here to place, and none to report
                // back as unmatched either, so it is dropped exactly as a null tag is - not turned into a 500.
                continue;
            }
            switch (communityAccess.resolveMember(communityId, query)) {
                case MemberMatch.Matched m -> {
                    userIds.add(m.userId());
                    assigned.add(query);
                }
                case MemberMatch.Ambiguous ignored -> ambiguous.add(query);
                case MemberMatch.NotFound ignored -> unmatched.add(query);
            }
        }
        return new ResolvedPresenters(List.copyOf(userIds),
                PresenterResolution.builder().assigned(List.copyOf(assigned)).unmatched(List.copyOf(unmatched))
                        .ambiguous(List.copyOf(ambiguous)).build());
    }
}
