package dev.axonia.expertise.fake;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import dev.axonia.expertise.NamedArea;
import dev.axonia.expertise.internal.NamedAreaRepository;

/**
 * In-memory {@link NamedAreaRepository} keyed on (organization, community), holding the rule the database enforces: a
 * tag sits in at most one named area of a community.
 */
public class FakeNamedAreaRepository implements NamedAreaRepository {

    private final Map<String, Map<Long, List<Stored>>> byOrg = new HashMap<>();
    private long nextId = 1;

    @Override
    public List<NamedArea> findAll(String organizationId, long communityId) {

        return community(organizationId, communityId).stream()
                .sorted(Comparator.comparing(Stored::name).thenComparingLong(Stored::id))
                .map(stored -> new NamedArea(stored.id(), stored.name(), List.copyOf(stored.tags()))).toList();
    }

    @Override
    public long create(String organizationId, long communityId, String name) {

        var id = nextId++;
        community(organizationId, communityId).add(new Stored(id, name, new TreeSet<>()));
        return id;
    }

    @Override
    public boolean rename(String organizationId, long communityId, long id, String name) {

        var areas = community(organizationId, communityId);
        var found = find(areas, id);
        found.ifPresent(stored -> areas.set(areas.indexOf(stored), new Stored(id, name, stored.tags())));
        return found.isPresent();
    }

    @Override
    public boolean delete(String organizationId, long communityId, long id) {

        return community(organizationId, communityId).removeIf(stored -> stored.id() == id);
    }

    @Override
    public boolean place(String organizationId, long communityId, long id, String canonicalTag) {

        var areas = community(organizationId, communityId);
        var found = find(areas, id);
        found.ifPresent(target -> {
            areas.forEach(stored -> stored.tags().remove(canonicalTag));
            target.tags().add(canonicalTag);
        });
        return found.isPresent();
    }

    @Override
    public void remove(String organizationId, long communityId, String canonicalTag) {

        community(organizationId, communityId).forEach(stored -> stored.tags().remove(canonicalTag));
    }

    @Override
    public void removeNaming(String organizationId, long communityId, String tag) {

        remove(organizationId, communityId, tag);
    }

    @Override
    public void renameTag(String organizationId, long communityId, String from, String to) {

        var areas = community(organizationId, communityId);
        var toIsPlaced = areas.stream().anyMatch(stored -> stored.tags().contains(to));
        for (var stored : areas) {
            if (stored.tags().remove(from) && !toIsPlaced) {
                stored.tags().add(to);
            }
        }
    }

    private static Optional<Stored> find(List<Stored> areas, long id) {

        return areas.stream().filter(stored -> stored.id() == id).findFirst();
    }

    private List<Stored> community(String organizationId, long communityId) {

        return byOrg.computeIfAbsent(organizationId, org -> new HashMap<>()).computeIfAbsent(communityId,
                id -> new ArrayList<>());
    }

    private record Stored(long id, String name, TreeSet<String> tags) {
    }
}
