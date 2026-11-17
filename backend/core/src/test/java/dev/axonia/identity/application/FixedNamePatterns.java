package dev.axonia.identity.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.axonia.identity.application.port.out.NamePatternLock;
import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.domain.NamePatterns;

/** An in-memory {@link NamePatternStore}, so a mocked suite reads names through the real {@link MemberNames}. */
public final class FixedNamePatterns implements NamePatternStore {

    /** No other transaction runs beside a mocked suite, so there is nothing to serialise against. */
    public static final NamePatternLock NO_LOCK = new NamePatternLock() {

        @Override
        public void shared(String organizationId) {
            // nothing runs concurrently in a mocked suite
        }

        @Override
        public void exclusive(String organizationId) {
            // nothing runs concurrently in a mocked suite
        }
    };

    private final Map<String, NamePatterns> byOrganization = new HashMap<>();

    public static MemberNames none() {

        return new MemberNames(new FixedNamePatterns(), NO_LOCK);
    }

    public static MemberNames declaring(String organizationId, String... patterns) {

        var store = new FixedNamePatterns();
        store.replace(organizationId, NamePatterns.of(List.of(patterns)));
        return new MemberNames(store, NO_LOCK);
    }

    @Override
    public NamePatterns find(String organizationId) {

        return byOrganization.getOrDefault(organizationId, NamePatterns.NONE);
    }

    @Override
    public void replace(String organizationId, NamePatterns patterns) {

        byOrganization.put(organizationId, patterns);
    }
}
