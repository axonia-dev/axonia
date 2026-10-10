package dev.axonia.events.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SectionTypeRecordMother {

    public static SectionTypeRecord.SectionTypeRecordBuilder any() {

        return SectionTypeRecord.builder().organizationId("acme").label("Lightning Talk").expectsPresenter(true);
    }
}
