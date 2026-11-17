package dev.axonia.mentoring.adapter.out.titles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.knowledge.api.ArtifactTitles;
import dev.axonia.mentoring.application.port.out.PointerTitles;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerKind;
import dev.axonia.mentoring.domain.PointerTarget;
import lombok.RequiredArgsConstructor;

/** One lookup per kind, whichever guild of the organization the target lives in: a session, an artifact. */
@Component("mentoringPointerTitlesAdapter")
@RequiredArgsConstructor
class PointerTitlesAdapter implements PointerTitles {

    private final ActivityTitles activityTitles;
    private final ArtifactTitles artifactTitles;

    @Override
    public Map<Long, PointerTarget> resolve(List<Pointer> pointers) {

        var targets = new HashMap<Long, PointerTarget>();
        resolved(pointers, PointerKind.SESSION,
                ids -> targetsOf(activityTitles.sessionNames(ids), s -> new PointerTarget(s.name(), s.communityId())),
                targets);
        resolved(pointers, PointerKind.ARTIFACT, ids -> targetsOf(artifactTitles.artifactsByIds(ids),
                a -> new PointerTarget(a.title(), a.communityId())), targets);
        return targets;
    }

    private static <T> Map<Long, PointerTarget> targetsOf(Map<Long, T> found, Function<T, PointerTarget> target) {

        var targets = new HashMap<Long, PointerTarget>();
        found.forEach((id, value) -> targets.put(id, target.apply(value)));
        return targets;
    }

    private static void resolved(List<Pointer> pointers, PointerKind kind,
            Function<List<Long>, Map<Long, PointerTarget>> lookup, Map<Long, PointerTarget> targets) {

        var ofKind = pointers.stream().filter(p -> p.kind() == kind).toList();
        if (ofKind.isEmpty()) {
            return;
        }
        var found = lookup.apply(ofKind.stream().map(Pointer::targetId).distinct().toList());
        ofKind.stream().filter(p -> found.containsKey(p.targetId()))
                .forEach(p -> targets.put(p.id(), found.get(p.targetId())));
    }
}
