import { describe, expect, it } from "vitest";

import {
  ALL_COMMUNITIES,
  type AreaFilters,
  filterAreas,
  sortAreas,
  totalSignals,
} from "@/features/expertise/domain/area-filter";
import type {
  EvidenceItem,
  SelfArea,
} from "@/features/expertise/domain/expertise";

function evidenceItem(occurredAt: string): EvidenceItem {
  return {
    signalType: "PRESENTED",
    sourceType: "SLOT",
    sourceId: 1,
    label: null,
    communitySlug: null,
    occurredAt,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
  };
}

function area(canonicalTag: string, over: Partial<SelfArea> = {}): SelfArea {
  return {
    canonicalTag,
    tier: "PRACTITIONER",
    hidden: false,
    communities: [{ id: 10, slug: "eng", name: "Eng" }],
    evidence: {
      presentedCount: 0,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    communityTiers: [],
    ...over,
  };
}

const base = (over: Partial<AreaFilters> = {}): AreaFilters => ({
  query: "",
  communityId: ALL_COMMUNITIES,
  hasTalks: false,
  hasArtifacts: false,
  hiddenOnly: false,
  ...over,
});

describe("filterAreas", () => {
  it("matches query against canonicalTag, case-insensitively", () => {
    const areas = [area("Kafka"), area("Rust")];
    expect(
      filterAreas(areas, base({ query: "kaf" })).map((a) => a.canonicalTag),
    ).toEqual(["Kafka"]);
  });

  it("keeps only areas with presentedCount > 0 when hasTalks is set", () => {
    const areas = [
      area("kafka", {
        evidence: {
          presentedCount: 1,
          contributedCount: 0,
          offeredCount: 0,
          recent: [],
        },
      }),
      area("rust", {
        evidence: {
          presentedCount: 0,
          contributedCount: 0,
          offeredCount: 0,
          recent: [],
        },
      }),
    ];
    expect(
      filterAreas(areas, base({ hasTalks: true })).map((a) => a.canonicalTag),
    ).toEqual(["kafka"]);
  });

  it("keeps only areas with contributedCount > 0 when hasArtifacts is set", () => {
    const areas = [
      area("kafka", {
        evidence: {
          presentedCount: 0,
          contributedCount: 1,
          offeredCount: 0,
          recent: [],
        },
      }),
      area("rust", {
        evidence: {
          presentedCount: 0,
          contributedCount: 0,
          offeredCount: 0,
          recent: [],
        },
      }),
    ];
    expect(
      filterAreas(areas, base({ hasArtifacts: true })).map(
        (a) => a.canonicalTag,
      ),
    ).toEqual(["kafka"]);
  });

  it("keeps only hidden areas when hiddenOnly is set", () => {
    const areas = [
      area("kafka", { hidden: true }),
      area("rust", { hidden: false }),
    ];
    expect(
      filterAreas(areas, base({ hiddenOnly: true })).map((a) => a.canonicalTag),
    ).toEqual(["kafka"]);
  });

  it("filters by community lens", () => {
    const areas = [
      area("kafka", { communities: [{ id: 10, slug: "eng", name: "Eng" }] }),
      area("rust", { communities: [{ id: 20, slug: "plat", name: "Plat" }] }),
    ];
    expect(
      filterAreas(areas, base({ communityId: 10 })).map((a) => a.canonicalTag),
    ).toEqual(["kafka"]);
  });

  it("passes everything through when communityId is ALL_COMMUNITIES", () => {
    const areas = [
      area("kafka", { communities: [{ id: 10, slug: "eng", name: "Eng" }] }),
      area("rust", { communities: [{ id: 20, slug: "plat", name: "Plat" }] }),
    ];
    expect(
      filterAreas(areas, base({ communityId: ALL_COMMUNITIES })),
    ).toHaveLength(2);
  });

  it("AND-combines chips", () => {
    const areas = [
      area("kafka", {
        hidden: true,
        evidence: {
          presentedCount: 1,
          contributedCount: 0,
          offeredCount: 0,
          recent: [],
        },
      }),
      area("rust", {
        hidden: true,
        evidence: {
          presentedCount: 0,
          contributedCount: 0,
          offeredCount: 0,
          recent: [],
        },
      }),
      area("go", {
        hidden: false,
        evidence: {
          presentedCount: 1,
          contributedCount: 0,
          offeredCount: 0,
          recent: [],
        },
      }),
    ];
    expect(
      filterAreas(areas, base({ hasTalks: true, hiddenOnly: true })).map(
        (a) => a.canonicalTag,
      ),
    ).toEqual(["kafka"]);
  });
});

describe("totalSignals", () => {
  it("sums presented, contributed, and offered counts", () => {
    const a = area("kafka", {
      evidence: {
        presentedCount: 2,
        contributedCount: 3,
        offeredCount: 4,
        recent: [],
      },
    });
    expect(totalSignals(a)).toBe(9);
  });
});

describe("sortAreas", () => {
  const a = area("beta", {
    evidence: {
      presentedCount: 1,
      contributedCount: 0,
      offeredCount: 0,
      recent: [evidenceItem("2026-01-01T00:00:00Z")],
    },
  });
  const b = area("alpha", {
    evidence: {
      presentedCount: 3,
      contributedCount: 3,
      offeredCount: 3,
      recent: [evidenceItem("2026-03-01T00:00:00Z")],
    },
  });

  it("depth: highest total signals first", () => {
    expect(sortAreas([a, b], "depth").map((x) => x.canonicalTag)).toEqual([
      "alpha",
      "beta",
    ]);
  });

  it("recent: newest evidence.recent occurredAt first, empty recent sorts last", () => {
    const c = area("gamma", {
      evidence: {
        presentedCount: 0,
        contributedCount: 0,
        offeredCount: 0,
        recent: [],
      },
    });
    expect(sortAreas([a, b, c], "recent").map((x) => x.canonicalTag)).toEqual([
      "alpha",
      "beta",
      "gamma",
    ]);
  });

  it("recent: uses the newest item when recent has multiple entries", () => {
    const withMultiple = area("delta", {
      evidence: {
        presentedCount: 0,
        contributedCount: 0,
        offeredCount: 0,
        recent: [
          evidenceItem("2026-01-01T00:00:00Z"),
          evidenceItem("2026-05-01T00:00:00Z"),
          evidenceItem("2026-02-01T00:00:00Z"),
        ],
      },
    });
    expect(
      sortAreas([a, withMultiple], "recent").map((x) => x.canonicalTag),
    ).toEqual(["delta", "beta"]);
  });

  it("az: alphabetical by canonicalTag", () => {
    expect(sortAreas([a, b], "az").map((x) => x.canonicalTag)).toEqual([
      "alpha",
      "beta",
    ]);
  });

  it("does not mutate the input", () => {
    const input = [a, b];
    sortAreas(input, "az");
    expect(input.map((x) => x.canonicalTag)).toEqual(["beta", "alpha"]);
  });
});
