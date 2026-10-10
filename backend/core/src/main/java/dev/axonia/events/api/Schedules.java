package dev.axonia.events.api;

import java.time.Instant;
import java.util.List;

public interface Schedules {

    List<ScheduleEntry> schedule(String slug, Instant from, Instant to);
}
