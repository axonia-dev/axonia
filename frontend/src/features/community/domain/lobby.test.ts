import { describe, expect, it } from "vitest";

import {
  GRID_PAGE_SIZE,
  lobbyItemSchema,
  lobbySchema,
  SHELF_PAGE_SIZE,
  shelfPageSize,
} from "@/features/community/domain/lobby";

const item = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE",
  memberCount: 3,
  mentoringEnabled: false,
  badgeUrl: null,
  heroUrl: null,
  accentColor: null,
  heroFocus: null,
  badgeFocus: null,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
  role: "MEMBER",
  nextSession: {
    title: "Kafka deep dive",
    scheduledAt: "2026-11-19T15:00:00Z",
    hasTime: true,
    zone: "Europe/Zurich",
  },
  nextSessionThisWeek: true,
  latestSession: {
    title: null,
    scheduledAt: "2026-11-02T23:00:00Z",
    hasTime: false,
    zone: "Europe/Zurich",
  },
  joinedLast30Days: 4,
  faces: ["alice", "bob"],
  stats: { sessionsHeld: 7, artifactsShared: 12 },
  lately: { artifactTitle: "Outbox pattern", topicTitle: null },
};

const empty = {
  ...item,
  role: null,
  nextSession: null,
  nextSessionThisWeek: false,
  latestSession: null,
  joinedLast30Days: 0,
  faces: ["alice"],
  stats: { sessionsHeld: 0, artifactsShared: 0 },
  lately: { artifactTitle: null, topicTitle: null },
};

describe("lobbyItemSchema", () => {
  it("parses a showcase item with the community fields unwrapped beside its signals", () => {
    // when
    const actual = lobbyItemSchema.parse(item);

    // then
    expect(actual).toEqual(item);
  });

  it("parses a community with no session, artifact or topic", () => {
    // when
    const actual = lobbyItemSchema.parse(empty);

    // then
    expect(actual.nextSession).toBeNull();
    expect(actual.latestSession).toBeNull();
    expect(actual.lately).toEqual({ artifactTitle: null, topicTitle: null });
  });

  it("rejects an item without its signals", () => {
    // given
    const withoutStats: Record<string, unknown> = { ...item };
    delete withoutStats.stats;

    // when
    const actual = lobbyItemSchema.safeParse(withoutStats);

    // then
    expect(actual.success).toBe(false);
  });

  it("rejects a session without the zone its day is read in", () => {
    // when
    const actual = lobbyItemSchema.safeParse({
      ...item,
      nextSession: {
        title: null,
        scheduledAt: "2026-11-19T15:00:00Z",
        hasTime: false,
      },
    });

    // then
    expect(actual.success).toBe(false);
  });

  it("rejects a session that does not say whether it has a time", () => {
    // when
    const actual = lobbyItemSchema.safeParse({
      ...item,
      nextSession: { title: null, scheduledAt: "2026-11-19T15:00:00Z" },
    });

    // then
    expect(actual.success).toBe(false);
  });
});

describe("lobbySchema", () => {
  it("parses the billboard and the keyed shelves", () => {
    // given
    const lobby = {
      billboard: [empty],
      shelves: [
        { key: "JOINED", items: [item], hasMore: false },
        { key: "OTHERS", items: [empty], hasMore: true },
      ],
    };

    // when
    const actual = lobbySchema.parse(lobby);

    // then
    expect(actual.billboard).toHaveLength(1);
    expect(actual.shelves.map((shelf) => shelf.key)).toEqual([
      "JOINED",
      "OTHERS",
    ]);
    expect(actual.shelves[1]?.hasMore).toBe(true);
  });

  it("rejects an unknown shelf key", () => {
    // when
    const actual = lobbySchema.safeParse({
      billboard: [],
      shelves: [{ key: "TRENDING", items: [], hasMore: false }],
    });

    // then
    expect(actual.success).toBe(false);
  });
});

describe("shelfPageSize", () => {
  it.each([
    ["JOINED", SHELF_PAGE_SIZE],
    ["THIS_WEEK", SHELF_PAGE_SIZE],
    ["GROWING", SHELF_PAGE_SIZE],
    ["OTHERS", GRID_PAGE_SIZE],
    ["EXPLORE", GRID_PAGE_SIZE],
  ] as const)("pages %s by %i", (key, expected) => {
    // when
    const actual = shelfPageSize(key);

    // then
    expect(actual).toBe(expected);
  });
});
