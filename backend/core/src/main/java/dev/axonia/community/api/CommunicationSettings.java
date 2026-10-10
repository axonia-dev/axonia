package dev.axonia.community.api;

public record CommunicationSettings(boolean notifySessionUpcoming, boolean notifySessionWrapUp,
        int sessionUpcomingLeadDays, int wrapUpGraceHours) {
}
