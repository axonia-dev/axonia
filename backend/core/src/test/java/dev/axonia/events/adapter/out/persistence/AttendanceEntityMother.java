package dev.axonia.events.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AttendanceEntityMother {

    public AttendanceEntity.AttendanceEntityBuilder any() {

        return AttendanceEntity.builder().organizationId("default").sessionId(1L).userId("alice");
    }
}
