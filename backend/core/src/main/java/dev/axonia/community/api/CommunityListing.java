package dev.axonia.community.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** A community as the directory lists it to one caller, with that caller's role in it, if any. */
public record CommunityListing(@JsonUnwrapped Community community, CommunityRole role) {
}
