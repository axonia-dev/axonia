package dev.axonia.health.domain;

/** How a question reads, worst first; QUIET means there is not enough to judge. */
public enum StatusLevel {
    WORRY, WATCH, GOOD, GREAT, QUIET
}
