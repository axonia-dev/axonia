package dev.axonia.events.mother;

import dev.axonia.events.internal.SectionTypeEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SectionTypeEntityMother {

    public static SectionTypeEntity.SectionTypeEntityBuilder any() {

        return SectionTypeEntity.builder().organizationId("acme").label("Lightning Talk").expectsPresenter(true);
    }
}
