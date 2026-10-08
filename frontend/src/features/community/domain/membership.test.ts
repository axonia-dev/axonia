import { describe, expect, it } from "vitest";

import {
  joinedSince,
  type Membership,
  membershipSchema,
} from "@/features/community/domain/membership";
import { pageSchema } from "@/features/community/domain/page";

const membership: Membership = {
  id: 9,
  communityId: 1,
  userId: "alice",
  role: "MEMBER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

describe("membershipSchema", () => {
  it("parses a valid membership", () => {
    // given the `membership` fixture above

    // when
    const actual = membershipSchema.parse(membership);

    // then
    expect(actual).toEqual(membership);
  });

  it("rejects an unknown role", () => {
    // given
    const invalid = { ...membership, role: "GUEST" };

    // when / then
    expect(() => membershipSchema.parse(invalid)).toThrow();
  });
});

describe("pageSchema", () => {
  it("parses a Spring page envelope and ignores extra keys", () => {
    // given
    const envelope = {
      content: [membership],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    };

    // when
    const actual = pageSchema(membershipSchema).parse(envelope);

    // then
    expect(actual.content).toHaveLength(1);
    expect(actual.totalElements).toBe(1);
  });
});

function member(id: number, joinedAt: string): Membership {
  return {
    id,
    communityId: 1,
    userId: `user-${id}`,
    role: "MEMBER",
    joinedAt,
    updatedAt: joinedAt,
  };
}

describe("joinedSince", () => {
  it("counts members who joined at or after the boundary", () => {
    // given
    const members = [
      member(1, "2026-06-05T10:00:00Z"), // this month
      member(2, "2026-06-01T00:00:00Z"), // exactly on the boundary, counts
      member(3, "2026-05-20T10:00:00Z"), // before, excluded
    ];

    // when
    const actual = joinedSince(members, "2026-06-01T00:00:00.000Z");

    // then
    expect(actual).toBe(2);
  });

  it("is zero for an empty list", () => {
    // when / then
    expect(joinedSince([], "2026-06-01T00:00:00.000Z")).toBe(0);
  });
});
