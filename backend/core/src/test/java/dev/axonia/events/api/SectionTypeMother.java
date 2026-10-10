package dev.axonia.events.api;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SectionTypeMother {

    public static SectionType.SectionTypeBuilder any() {

        return SectionType.builder().id(1L).label("Lightning Talk").expectsPresenter(true);
    }
}
