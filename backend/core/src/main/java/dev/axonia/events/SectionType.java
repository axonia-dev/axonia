package dev.axonia.events;

import lombok.Builder;

@Builder
public record SectionType(Long id, String label, boolean expectsPresenter, String icon, String accentColor) {
}
