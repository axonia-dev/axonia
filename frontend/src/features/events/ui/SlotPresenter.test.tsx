import { describe, expect, it } from "vitest";

import {
  displayNameOf,
  presenterByline,
} from "@/features/events/ui/SlotPresenter";
import type { User } from "@/features/identity/domain/user";

const users: Record<string, User> = {
  "uuid-alice": {
    id: "uuid-alice",
    displayName: "Alice Bauer",
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
};

describe("displayNameOf", () => {
  it("resolves a known id and falls back to 'Member'", () => {
    expect(displayNameOf("uuid-alice", users)).toBe("Alice Bauer");
    expect(displayNameOf("uuid-unknown", users)).toBe("Member");
  });
});

function user(displayName: string): User {
  return {
    id: displayName,
    displayName,
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  };
}

describe("presenterByline", () => {
  const byline: Record<string, User> = {
    a: user("Alice Anderson"),
    m: user("Mia Eriksson"),
    b: user("Bob Brown"),
  };

  it("returns null for no presenters", () => {
    expect(presenterByline([], byline)).toBeNull();
  });

  it("credits a sole presenter", () => {
    expect(presenterByline(["a"], byline)).toBe("by Alice Anderson");
  });

  it("joins two presenters with '&'", () => {
    expect(presenterByline(["a", "m"], byline)).toBe(
      "by Alice Anderson & Mia Eriksson",
    );
  });

  it("joins three or more presenters with commas and a trailing '&'", () => {
    expect(presenterByline(["a", "m", "b"], byline)).toBe(
      "by Alice Anderson, Mia Eriksson & Bob Brown",
    );
  });
});
