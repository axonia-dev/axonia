package dev.axonia.mentoring.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "axonia.mentoring.meeting-reminder")
record MeetingReminderSchedule(@DefaultValue("1h") Duration interval, @DefaultValue("7m") Duration initialDelay) {
}
