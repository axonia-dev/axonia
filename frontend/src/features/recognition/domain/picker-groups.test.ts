import { describe, expect, it } from "vitest";

import { COMMON_REACTIONS } from "@/features/recognition/domain/common-reactions";
import type { EmojiGroup } from "@/features/recognition/domain/emoji-search";
import {
  COMMON_HEADING,
  FREQUENT_HEADING,
  pickerGroups,
  type PickerGroupsInput,
} from "@/features/recognition/domain/picker-groups";
import type { ReactionDefinition } from "@/features/recognition/domain/reaction";

const CATALOG: EmojiGroup[] = [
  {
    group: "Smileys & Emotion",
    emoji: [
      { c: "😄", n: "grinning face with smiling eyes" },
      { c: "😢", n: "crying face" },
    ],
  },
  {
    group: "Travel & Places",
    emoji: [
      { c: "🚀", n: "rocket" },
      { c: "🔥", n: "fire" },
    ],
  },
  {
    group: "People & Body",
    emoji: [
      { c: "👍", n: "thumbs up" },
      { c: "👏", n: "clapping hands" },
      { c: "🙌", n: "raising hands" },
      { c: "🧠", n: "brain" },
      { c: "👀", n: "eyes" },
    ],
  },
  {
    group: "Activities",
    emoji: [
      { c: "🎉", n: "party popper" },
      { c: "🎯", n: "bullseye" },
    ],
  },
  { group: "Objects", emoji: [{ c: "💡", n: "light bulb" }] },
];

const SHIP_IT: ReactionDefinition = {
  id: 1,
  name: "ship-it",
  imageUrl: "https://cdn.example/ship-it.png",
  pending: false,
  proposedByUserId: null,
  usageCount: 0,
};

const WAITING: ReactionDefinition = {
  ...SHIP_IT,
  id: 2,
  name: "wip",
  pending: true,
};

function input(overrides: Partial<PickerGroupsInput> = {}): PickerGroupsInput {
  return {
    definitions: [],
    catalog: CATALOG,
    recents: [],
    term: "",
    organizationName: "Backend Guild",
    ...overrides,
  };
}

const headings = (groups: ReturnType<typeof pickerGroups>) =>
  groups.map((group) => group.heading);

describe("pickerGroups", () => {
  it("heads the organization's own reactions with its bare name", () => {
    const groups = pickerGroups(input({ definitions: [SHIP_IT] }));

    expect(groups[0]?.heading).toBe("Backend Guild");
  });

  it("falls back to a word that fits a hub and an enterprise until the name arrives", () => {
    const groups = pickerGroups(
      input({ definitions: [SHIP_IT], organizationName: undefined }),
    );

    expect(groups[0]?.heading).toBe("Organization");
  });

  it("leaves out a proposal still waiting, which is not a reaction yet", () => {
    const groups = pickerGroups(input({ definitions: [WAITING] }));

    expect(headings(groups)).not.toContain("Backend Guild");
  });

  it("puts the common set above the catalog and below the organization's own", () => {
    const groups = pickerGroups(input({ definitions: [SHIP_IT] }));

    expect(headings(groups).slice(0, 2)).toEqual([
      "Backend Guild",
      COMMON_HEADING,
    ]);
  });

  it("names a common reaction from the catalog rather than from a second copy", () => {
    const common = pickerGroups(input()).find(
      (g) => g.heading === COMMON_HEADING,
    );

    expect(common?.entries.map((entry) => entry.name)).toContain("thumbs up");
  });

  it("holds the whole common set, in the order it is declared", () => {
    const common = pickerGroups(input()).find(
      (g) => g.heading === COMMON_HEADING,
    );

    expect(common?.entries.map((entry) => entry.emoji)).toEqual([
      ...COMMON_REACTIONS,
    ]);
  });

  it("shows what this member reached for lately, newest first", () => {
    const groups = pickerGroups(input({ recents: ["🧠", "🎯"] }));
    const frequent = groups.find((g) => g.heading === FREQUENT_HEADING);

    expect(frequent?.entries.map((entry) => entry.emoji)).toEqual(["🧠", "🎯"]);
  });

  it("leaves the frequent group out entirely until something has been picked", () => {
    expect(headings(pickerGroups(input()))).not.toContain(FREQUENT_HEADING);
  });

  it("keeps a recent out of the frequent group when the common set already holds it", () => {
    const groups = pickerGroups(input({ recents: ["👍", "🧠"] }));
    const frequent = groups.find((g) => g.heading === FREQUENT_HEADING);

    expect(frequent?.entries.map((entry) => entry.emoji)).toEqual(["🧠"]);
  });

  it("drops a recent the catalog no longer carries rather than drawing it nameless", () => {
    const groups = pickerGroups(input({ recents: ["🛸", "🧠"] }));
    const frequent = groups.find((g) => g.heading === FREQUENT_HEADING);

    expect(frequent?.entries.map((entry) => entry.emoji)).toEqual(["🧠"]);
  });

  it("hides both curated groups while searching, so a result set stays one list", () => {
    const groups = pickerGroups(input({ recents: ["🧠"], term: "brain" }));

    expect(headings(groups)).toEqual(["People & Body"]);
  });

  it("matches the organization's own reactions by name while searching", () => {
    const groups = pickerGroups(
      input({ definitions: [SHIP_IT], term: "ship" }),
    );

    expect(headings(groups)).toEqual(["Backend Guild"]);
  });

  it("carries the ref a pick hands back, so the renderer builds none of its own", () => {
    const groups = pickerGroups(input({ definitions: [SHIP_IT] }));

    expect(groups[0]?.entries[0]?.ref).toEqual({
      kind: "CUSTOM",
      definitionId: 1,
    });
  });

  it("carries the image of a custom reaction and no character", () => {
    const entry = pickerGroups(input({ definitions: [SHIP_IT] }))[0]
      ?.entries[0];

    expect(entry?.imageUrl).toBe(SHIP_IT.imageUrl);
    expect(entry?.emoji).toBeNull();
  });

  it("gives entries keys that are unique inside their own group", () => {
    for (const group of pickerGroups(input({ definitions: [SHIP_IT] }))) {
      const keys = group.entries.map((entry) => entry.key);
      expect(new Set(keys).size).toBe(keys.length);
    }
  });

  it("renders nothing at all before the catalog has loaded", () => {
    expect(pickerGroups(input({ catalog: [], recents: ["🧠"] }))).toEqual([]);
  });
});
