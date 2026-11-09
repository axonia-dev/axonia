package dev.axonia.health.domain;

/** One question's reading: a word and its level. Never summed with another question's into a score. */
public record Status(StatusCode code, StatusLevel level) {

    public static Status of(StatusCode code) {

        return new Status(code, code.level());
    }
}
