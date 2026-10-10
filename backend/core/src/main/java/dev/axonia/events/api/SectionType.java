package dev.axonia.events.api;

import lombok.Builder;

@Builder
public record SectionType(Long id, String label, boolean expectsPresenter, String icon, String accentColor) {
}
