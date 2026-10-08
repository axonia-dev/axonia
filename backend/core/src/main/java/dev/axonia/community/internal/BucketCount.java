package dev.axonia.community.internal;

/** One grouped row of a bucketed count query. */
record BucketCount(int bucket, long n) {
}
