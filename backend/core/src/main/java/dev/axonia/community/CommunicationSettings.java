package dev.axonia.community;

public record CommunicationSettings(boolean notifySessionUpcoming, boolean notifySessionWrapUp,
        int sessionUpcomingLeadDays, int wrapUpGraceHours) {
}
