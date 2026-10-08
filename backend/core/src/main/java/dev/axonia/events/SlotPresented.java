package dev.axonia.events;

import java.util.List;

public record SlotPresented(Long slotId, Long communityId, List<String> presenterUserIds, List<String> tags) {
}
