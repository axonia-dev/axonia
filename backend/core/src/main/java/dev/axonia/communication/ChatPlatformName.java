package dev.axonia.communication;

/**
 * Which chat product a webhook URL belongs to. A leader declares it when saving a destination and the URL's host must
 * agree, so the two can never disagree and only the host is ever stored.
 */
public enum ChatPlatformName {
    SLACK, TEAMS
}
