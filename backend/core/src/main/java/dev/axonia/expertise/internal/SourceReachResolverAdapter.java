package dev.axonia.expertise.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.events.SlotAttendanceReader;
import dev.axonia.expertise.SourceReachResolver;
import dev.axonia.expertise.SourceRef;
import dev.axonia.expertise.SourceType;
import lombok.RequiredArgsConstructor;

/**
 * Resolves reach over the module that owns attendance, in one batched read, following
 * {@link SourceLabelResolverAdapter}: a miss is a value and never an exception, because a lookup that throws inside the
 * caller's read transaction marks it rollback-only before any catch here could run.
 */
@Component
@RequiredArgsConstructor
class SourceReachResolverAdapter implements SourceReachResolver {

    private final SlotAttendanceReader slotAttendanceReader;

    @Override
    public Map<SourceRef, Integer> reachFor(Collection<SourceRef> refs) {

        var slotIds = refs.stream().filter(ref -> ref.sourceType() == SourceType.SLOT).map(SourceRef::sourceId)
                .distinct().toList();

        if (slotIds.isEmpty()) {
            return Map.of();
        }

        var reach = new HashMap<SourceRef, Integer>();
        slotAttendanceReader.attendanceBySlot(slotIds)
                .forEach((slotId, count) -> reach.put(new SourceRef(SourceType.SLOT, slotId), count));
        return reach;
    }
}
