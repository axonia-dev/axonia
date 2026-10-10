package dev.axonia.events.api;

import java.util.List;

public record SlotPresented(Long slotId, Long communityId, List<String> presenterUserIds, List<String> tags) {
}
