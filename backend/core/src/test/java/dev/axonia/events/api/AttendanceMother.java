package dev.axonia.events.api;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AttendanceMother {

    public Attendance.AttendanceBuilder any() {

        return Attendance.builder().id(1L).sessionId(1L).userId("alice");
    }
}
