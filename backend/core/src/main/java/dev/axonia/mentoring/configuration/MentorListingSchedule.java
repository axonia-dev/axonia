package dev.axonia.mentoring.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "axonia.mentoring.listing")
record MentorListingSchedule(@DefaultValue("1h") Duration interval, @DefaultValue("1m") Duration initialDelay) {
}
