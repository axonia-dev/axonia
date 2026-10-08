package dev.axonia.events.mother;

import dev.axonia.events.Attendance;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AttendanceMother {

    public Attendance.AttendanceBuilder any() {

        return Attendance.builder().id(1L).sessionId(1L).userId("alice");
    }
}
