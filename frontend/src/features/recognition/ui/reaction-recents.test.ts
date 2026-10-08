import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { setCurrentUserId } from "@/auth/current-user";
import {
  parseRecents,
  RECENT_REACTIONS_LIMIT,
  rememberRecent,
  storedRecents,
  withRecent,
} from "@/features/recognition/ui/reaction-recents";

const MEMBER = "member-1";

beforeEach(() => {
  localStorage.clear();
  setCurrentUserId(MEMBER);
});

afterEach(() => {
  setCurrentUserId(undefined);
});

describe("withRecent", () => {
  it("puts the newest pick first", () => {
    expect(withRecent(["🎯"], "🧠")).toEqual(["🧠", "🎯"]);
  });

  it("moves a repeat to the front rather than listing it twice", () => {
    expect(withRecent(["🎯", "🧠", "👀"], "🧠")).toEqual(["🧠", "🎯", "👀"]);
  });

  it("drops the oldest once the list is full", () => {
    const full = Array.from({ length: RECENT_REACTIONS_LIMIT }, (_, i) =>
      String(i),
    );

    const next = withRecent(full, "🧠");

    expect(next).toHaveLength(RECENT_REACTIONS_LIMIT);
    expect(next.at(-1)).toBe(String(RECENT_REACTIONS_LIMIT - 2));
  });
});

describe("parseRecents", () => {
  it("reads a stored list back", () => {
    expect(parseRecents('["🧠","🎯"]')).toEqual(["🧠", "🎯"]);
  });

  it("reads nothing stored as no recents", () => {
    expect(parseRecents(null)).toEqual([]);
  });

  it("reads unparseable text as no recents rather than throwing", () => {
    expect(parseRecents("{not json")).toEqual([]);
  });

  it("reads a value of the wrong shape as no recents", () => {
    expect(parseRecents('{"a":1}')).toEqual([]);
  });

  it("keeps only the strings out of a mixed list", () => {
    expect(parseRecents('["🧠",7,null,"🎯"]')).toEqual(["🧠", "🎯"]);
  });
});

describe("reaction recents in storage", () => {
  it("hands back what was remembered", () => {
    rememberRecent("🧠");

    expect(storedRecents()).toEqual(["🧠"]);
  });

  it("keeps one member's recents away from another's", () => {
    rememberRecent("🧠");
    setCurrentUserId("member-2");

    expect(storedRecents()).toEqual([]);
  });

  it("remembers nothing for a caller with no member behind it", () => {
    setCurrentUserId(undefined);

    rememberRecent("🧠");

    expect(storedRecents()).toEqual([]);
  });
});
