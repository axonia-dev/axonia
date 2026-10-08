package dev.axonia.communication.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageSubjectPolicy;
import dev.axonia.communication.MessageType;

/**
 * The {@link MessageSubjectPolicy} that claims each {@link MessageType}, and so the types this edition sends: a type
 * exists here because a module claims it. An open type no policy claims fails construction - left to delivery, its rows
 * would be suppressed forever under a reason nothing configured - and so does a type two policies claim.
 */
@Component
public final class MessageSubjectPolicies {

    private final Map<MessageType, MessageSubjectPolicy> byType;

    MessageSubjectPolicies(List<MessageSubjectPolicy> policies) {

        byType = policies.stream().flatMap(policy -> policy.handles().stream().map(type -> Map.entry(type, policy)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        var unrouted = MessageType.open().stream().filter(type -> !byType.containsKey(type)).map(MessageType::code)
                .toList();
        if (!unrouted.isEmpty()) {
            throw new IllegalStateException("no MessageSubjectPolicy handles " + unrouted);
        }
    }

    public List<MessageType> types() {

        var contributed = byType.keySet().stream().filter(type -> !MessageType.open().contains(type))
                .sorted(Comparator.comparing(MessageType::code));
        return Stream.concat(MessageType.open().stream(), contributed).toList();
    }

    public boolean declares(MessageType type) {

        return byType.containsKey(type);
    }

    Optional<MessageSubjectPolicy> policyFor(MessageType type) {

        return Optional.ofNullable(byType.get(type));
    }
}
