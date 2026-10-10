package dev.axonia.expertise.domain;

/**
 * Two areas the same people practise, weighted by how many people practise both. {@code a} sorts before {@code b}, so
 * each pair appears exactly once.
 */
public record TopicLink(String a, String b, int weight) {
}
