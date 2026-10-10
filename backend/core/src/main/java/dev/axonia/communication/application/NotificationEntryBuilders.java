package dev.axonia.communication.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.communication.api.MessageType;

/**
 * Turns one message's frozen facts into the four strings a panel row shows. Reads exactly the keys the open senders
 * write - {@code events}' {@code SessionFacts} and {@code roundup}'s {@code RoundupFacts} - which are not linkable from
 * here, since {@code communication} depends on neither, and a contributed type's keys from its {@link InAppEntryShape}.
 *
 * <p>
 * Every type a module sends reaches a member's panel, because the in-app channel is unconditional: an open type has its
 * shape here, a contributed type must contribute one, and the hosted edition's
 * {@code EveryMessageTypeReachesThePanelTest} holds every declared type to that.
 */
@Component
public class NotificationEntryBuilders {

    private final Map<MessageType, InAppEntryShape> contributed;

    NotificationEntryBuilders(List<InAppEntryShape> shapes) {

        this.contributed = shapes.stream()
                .collect(Collectors.toUnmodifiableMap(InAppEntryShape::type, Function.identity()));
    }

    public Optional<PartialEntry> build(MessageType type, Map<String, Object> facts) {

        if (MessageType.SESSION_UPCOMING.equals(type)) {
            return Optional.of(session(facts, FactStrings.when(facts, "Happening")));
        }
        if (MessageType.SESSION_WRAP_UP.equals(type)) {
            return Optional.of(session(facts, FactStrings.when(facts, "Held")));
        }
        if (MessageType.SESSION_AWAITING_CLOSE.equals(type)) {
            return Optional.of(session(facts, "Waiting to be closed out"));
        }
        if (MessageType.ORGANIZATION_ROUNDUP.equals(type)) {
            return Optional.of(shaped(facts, "periodLabel", "communitiesUrl"));
        }
        return Optional.ofNullable(contributed.get(type))
                .map(shape -> shaped(facts, shape.lineFact(), shape.actionUrlFact()));
    }

    private PartialEntry session(Map<String, Object> facts, String line) {

        return new PartialEntry(FactStrings.senderName(facts), FactStrings.headline(facts), line,
                FactStrings.text(facts.get("sessionUrl")));
    }

    private PartialEntry shaped(Map<String, Object> facts, String lineFact, String actionUrlFact) {

        return new PartialEntry(FactStrings.senderName(facts), FactStrings.headline(facts),
                FactStrings.text(facts.get(lineFact)), FactStrings.text(facts.get(actionUrlFact)));
    }

    public record PartialEntry(String sender, String headline, String line, String actionUrl) {
    }
}
