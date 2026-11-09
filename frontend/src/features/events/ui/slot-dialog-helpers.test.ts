import { describe, expect, it } from "vitest";

import type { Topic } from "@/features/events/domain/topic";
import {
  modeOptions,
  topicDescription,
} from "@/features/events/ui/slot-dialog-helpers";

function topic(overrides: Partial<Topic>): Topic {
  return {
    id: 1,
    communityId: 1,
    kind: "OFFER",
    title: "A title",
    description: "A description",
    tags: [],
    proposerUserId: "user-1",
    proposerDisplayName: "Alice",
    speakerUserIds: [],
    canManageSpeakers: false,
    canClose: false,
    canReopen: false,
    voteCount: 0,
    hasVoted: false,
    canEdit: false,
    canDelete: false,
    status: "PROPOSED",
    scheduledFor: null,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("topicDescription", () => {
  it("returns the matched topic's description (not its title)", () => {
    const ranked = [
      topic({
        id: 7,
        title: "Event Sourcing",
        description: "How to event source",
      }),
      topic({ id: 8, title: "CQRS", description: "Command query separation" }),
    ];
    expect(topicDescription(ranked, 8)).toBe("Command query separation");
  });

  it("returns an empty string for an unknown id", () => {
    const ranked = [topic({ id: 7, description: "How to event source" })];
    expect(topicDescription(ranked, 99)).toBe("");
  });

  it("returns an empty string for a null id", () => {
    const ranked = [topic({ id: 7, description: "How to event source" })];
    expect(topicDescription(ranked, null)).toBe("");
  });
});

describe("modeOptions", () => {
  it("offers to leave the slot open for a topic nobody presents yet", () => {
    expect(modeOptions(topic({ speakerUserIds: [] }))).toEqual([
      { id: "present", label: "I'll present" },
      { id: "open", label: "Leave open" },
    ]);
  });

  it("names what queuing does for a topic that already has speakers", () => {
    expect(modeOptions(topic({ speakerUserIds: ["bob"] }))).toEqual([
      { id: "present", label: "I'll present" },
      { id: "open", label: "Speakers present" },
    ]);
  });

  it("offers to leave the slot open while no topic is picked", () => {
    expect(modeOptions(null)[1]).toEqual({ id: "open", label: "Leave open" });
  });
});
