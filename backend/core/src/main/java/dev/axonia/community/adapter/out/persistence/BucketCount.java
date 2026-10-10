package dev.axonia.community.adapter.out.persistence;

/** One grouped row of a bucketed count query. */
public record BucketCount(int bucket, long n) {
}
