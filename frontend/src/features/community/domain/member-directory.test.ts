import { describe, expect, it } from "vitest";

import {
  countByRole,
  filterMembers,
  formatJoinedMonth,
} from "@/features/community/domain/member-directory";
import type { Membership } from "@/features/community/domain/membership";
import type { User } from "@/features/identity/domain/user";

function member(over: Partial<Membership>): Membership {
  return {
    id: 1,
    communityId: 1,
    userId: "u1",
    role: "MEMBER",
    joinedAt: "2026-04-02T00:00:00Z",
    updatedAt: "2026-04-02T00:00:00Z",
    ...over,
  };
}

function user(over: Partial<User> & { id: string }): User {
  return {
    displayName: "Anon",
    email: null,
    avatarUrl: null,
    department: null,
    businessUnit: null,
    team: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
    ...over,
  };
}

const roster: Membership[] = [
  member({ id: 1, userId: "alice", role: "ORGANIZER" }),
  member({ id: 2, userId: "bob", role: "MEMBER" }),
  member({ id: 3, userId: "carol", role: "MEMBER" }),
];

const users: Record<string, User> = {
  alice: user({ id: "alice", displayName: "Alice Wong", team: "Payments" }),
  bob: user({
    id: "bob",
    displayName: "Bob Lee",
    department: "Design Systems",
  }),
  carol: user({ id: "carol", displayName: "Carol Diaz", team: "SRE" }),
};

describe("countByRole", () => {
  it("counts all, organizers and members", () => {
    expect(countByRole(roster)).toEqual({ all: 3, organizers: 1, members: 2 });
  });
});

describe("filterMembers", () => {
  it("returns everyone for the all segment with no query", () => {
    expect(filterMembers(roster, users, "", "all")).toHaveLength(3);
  });
  it("keeps only organizers for the organizers segment", () => {
    expect(
      filterMembers(roster, users, "", "organizers").map((m) => m.userId),
    ).toEqual(["alice"]);
  });
  it("keeps only members for the members segment", () => {
    expect(
      filterMembers(roster, users, "", "members").map((m) => m.userId),
    ).toEqual(["bob", "carol"]);
  });
  it("matches the query against display name", () => {
    expect(
      filterMembers(roster, users, "alice", "all").map((m) => m.userId),
    ).toEqual(["alice"]);
  });
  it("matches the query against team or department", () => {
    expect(
      filterMembers(roster, users, "design", "all").map((m) => m.userId),
    ).toEqual(["bob"]);
  });
  it("falls back to the userId when the user is unresolved", () => {
    expect(
      filterMembers(roster, {}, "carol", "all").map((m) => m.userId),
    ).toEqual(["carol"]);
  });
});

describe("formatJoinedMonth", () => {
  it("formats a valid instant as short month and year", () => {
    expect(formatJoinedMonth("2026-04-02T00:00:00Z")).toMatch(/Apr 2026/);
  });
  it("returns an empty string for an unparseable date", () => {
    expect(formatJoinedMonth("not-a-date")).toBe("");
  });
});
