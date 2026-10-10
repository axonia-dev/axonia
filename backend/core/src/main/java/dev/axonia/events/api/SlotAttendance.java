package dev.axonia.events.api;

import java.util.Collection;
import java.util.Map;

/** How many people attended the session each slot belongs to; absent means unknown, zero means measured and empty. */
public interface SlotAttendance {

    Map<Long, Integer> attendanceBySlot(Collection<Long> slotIds);
}
