package dev.axonia.shared.api.personaldata;

import java.time.Instant;

import lombok.Builder;

/** One member's erasure within one Organization. A record rather than four parameters, which PMD would refuse. */
@Builder
public record MemberErasure(String organizationId, String userId, String surrogateId, Instant erasedAt) {
}
