package dev.axonia.identity.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.domain.NamePatterns;

/** An in-memory {@link NamePatternStore}, so a mocked suite reads names through the real {@link MemberNames}. */
public final class FixedNamePatterns implements NamePatternStore {

    private final Map<String, NamePatterns> byOrganization = new HashMap<>();

    public static MemberNames none() {

        return new MemberNames(new FixedNamePatterns());
    }

    public static MemberNames declaring(String organizationId, String... patterns) {

        var store = new FixedNamePatterns();
        store.replace(organizationId, NamePatterns.of(List.of(patterns)));
        return new MemberNames(store);
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
