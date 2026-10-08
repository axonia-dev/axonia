import { describe, expect, it } from "vitest";

import {
  ALL_COMMUNITIES,
  ALL_TYPES,
  filterSaved,
  isToReview,
  type SavedFilters,
  savedItemTitle,
  scopeToCommunity,
  sortSaved,
  toReviewCount,
} from "@/features/bookmarks/domain/saved-filter";
import {
  sessionView,
  view,
} from "@/features/bookmarks/domain/saved-filter-fixture";

const base: SavedFilters = {
  query: "",
  communityId: ALL_COMMUNITIES,
  kind: "all",
  typeId: ALL_TYPES,
  reviewOnly: false,
};

describe("filterSaved", () => {
  it("matches the query against title, description and tags", () => {
    const items = [
      view({ id: 1, title: "Kafka rebalancing" }),
      view({ id: 2, title: "Other", description: "deep kafka dive" }),
      view({ id: 3, title: "Other", tags: ["kafka"] }),
      view({ id: 4, title: "Unrelated" }),
    ];
    const got = filterSaved(items, { ...base, query: "kafka" });
    expect(
      got.map((v) => (v.kind === "artifact" ? v.artifact.id : null)),
    ).toEqual([1, 2, 3]);
  });

  it("matches the query against a session's title", () => {
    const items = [
      sessionView({ id: 1, title: "Kafka office hours" }),
      sessionView({ id: 2, title: "Unrelated retro" }),
    ];
    const got = filterSaved(items, { ...base, query: "kafka" });
    expect(got).toHaveLength(1);
    expect(got[0]).toBe(items[0]);
  });

  it("filters by community and type", () => {
    const items = [
      view({ id: 1, communityId: 2, typeId: 3 }),
      view({ id: 2, communityId: 9, typeId: 3 }),
      view({ id: 3, communityId: 2, typeId: 7 }),
    ];
    expect(
      filterSaved(items, { ...base, communityId: 2 }).map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([1, 3]);
    expect(
      filterSaved(items, { ...base, typeId: 7 }).map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([3]);
  });

  it("reviewOnly keeps only items not marked helpful", () => {
    const items = [
      view({ id: 1, helped: true }),
      view({ id: 2, helped: null }),
    ];
    expect(
      filterSaved(items, { ...base, reviewOnly: true }).map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([2]);
  });

  it("kind: session drops artifacts; kind: artifact drops sessions", () => {
    const items = [view({ id: 1 }), sessionView({ id: 10 })];

    const sessionsOnly = filterSaved(items, { ...base, kind: "session" });
    expect(sessionsOnly).toEqual([items[1]]);

    const artifactsOnly = filterSaved(items, { ...base, kind: "artifact" });
    expect(artifactsOnly).toEqual([items[0]]);

    expect(filterSaved(items, { ...base, kind: "all" })).toEqual(items);
  });

  it("a specific typeId narrows artifacts only, keeping all sessions", () => {
    const items = [
      view({ id: 1, typeId: 7 }),
      view({ id: 2, typeId: 3 }),
      sessionView({ id: 10 }),
    ];
    const got = filterSaved(items, { ...base, typeId: 7 });
    expect(got).toEqual([items[0], items[2]]);
    expect(got).toContain(items[2]);
  });

  it("reviewOnly drops sessions, which are never to review", () => {
    const items = [view({ id: 1, helped: null }), sessionView({ id: 10 })];
    expect(filterSaved(items, { ...base, reviewOnly: true })).toEqual([
      items[0],
    ]);
  });
});

describe("scopeToCommunity", () => {
  it("keeps only items belonging to the given community, sessions included", () => {
    const kept = [
      view({ id: 2, communityId: 9 }),
      sessionView({ communityId: 9 }),
    ];
    const items = [view({ id: 1, communityId: 2 }), ...kept];
    expect(scopeToCommunity(items, 9)).toEqual(kept);
  });

  it("keeps nothing while the community is not yet resolved", () => {
    expect(scopeToCommunity([view({ id: 1 })], undefined)).toEqual([]);
  });
});

describe("sortSaved", () => {
  const items = [
    view({
      id: 1,
      title: "Beta",
      savedAt: "2026-06-01T00:00:00Z",
      loveCount: 5,
    }),
    view({
      id: 2,
      title: "Alpha",
      savedAt: "2026-06-03T00:00:00Z",
      loveCount: 1,
    }),
    view({
      id: 3,
      title: "Gamma",
      savedAt: "2026-06-02T00:00:00Z",
      loveCount: 9,
    }),
  ];
  it("orders by recent, oldest, loved and A-Z", () => {
    expect(
      sortSaved(items, "recent").map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([2, 3, 1]);
    expect(
      sortSaved(items, "oldest").map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([1, 3, 2]);
    expect(
      sortSaved(items, "loved").map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([3, 1, 2]);
    expect(
      sortSaved(items, "az").map((v) =>
        v.kind === "artifact" ? v.artifact.id : null,
      ),
    ).toEqual([2, 1, 3]);
  });
  it("does not mutate its input", () => {
    const copy = [...items];
    sortSaved(items, "az");
    expect(items).toEqual(copy);
  });

  it("orders az by savedItemTitle across kinds", () => {
    const mixed = [
      view({ id: 1, title: "Zeta artifact" }),
      sessionView({ id: 10, title: "Alpha session" }),
      view({ id: 2, title: "Mid artifact" }),
    ];
    expect(sortSaved(mixed, "az").map(savedItemTitle)).toEqual([
      "Alpha session",
      "Mid artifact",
      "Zeta artifact",
    ]);
  });

  it("loved sinks sessions (love 0) below loved artifacts", () => {
    const mixed = [
      sessionView({ id: 10 }),
      view({ id: 1, loveCount: 3 }),
      view({ id: 2, loveCount: 1 }),
    ];
    expect(sortSaved(mixed, "loved").map(savedItemTitle)).toEqual([
      savedItemTitle(mixed[1]!),
      savedItemTitle(mixed[2]!),
      savedItemTitle(mixed[0]!),
    ]);
  });
});

describe("savedItemTitle", () => {
  it("uses the artifact title for artifacts", () => {
    expect(savedItemTitle(view({ title: "RFC 42" }))).toBe("RFC 42");
  });

  it("uses the session title, falling back to 'Session' when untitled", () => {
    expect(savedItemTitle(sessionView({ title: "Retro" }))).toBe("Retro");
    expect(savedItemTitle(sessionView({ title: null }))).toBe("Session");
  });
});

describe("triage", () => {
  it("counts artifact items not marked helpful", () => {
    const items = [
      view({ helped: true }),
      view({ helped: null }),
      view({ helped: null }),
    ];
    expect(toReviewCount(items)).toBe(2);
    expect(isToReview(items[0]!)).toBe(false);
    expect(isToReview(items[1]!)).toBe(true);
  });

  it("never counts sessions as to-review", () => {
    const items = [sessionView({ id: 1 }), view({ helped: null })];
    expect(toReviewCount(items)).toBe(1);
    expect(isToReview(items[0]!)).toBe(false);
  });
});
