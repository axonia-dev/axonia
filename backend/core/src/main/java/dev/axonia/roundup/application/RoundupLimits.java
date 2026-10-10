package dev.axonia.roundup.application;

/** The caps the letter applies when it names communities, sessions and topics. */
public interface RoundupLimits {

    int communityCap();

    int sessionsPerCommunityCap();

    int topicCap();
}
