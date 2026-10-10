package dev.axonia.events.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AttendanceRecordMother {

    public AttendanceRecord.AttendanceRecordBuilder any() {

        return AttendanceRecord.builder().organizationId("default").sessionId(1L).userId("alice");
    }
}
