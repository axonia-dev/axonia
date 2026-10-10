package dev.axonia.expertise.domain;

/**
 * An order-normalized parked pair: {@code tagLow} sorts lexicographically before or equal to {@code tagHigh}.
 */
public record TagPair(String tagLow, String tagHigh) {
}
