import { describe, expect, it } from "vitest";

import {
  chatDestinationSchema,
  destinationDetail,
  formatChatTimestamp,
} from "@/features/community/domain/chat-destination";

describe("formatChatTimestamp", () => {
  it("renders an instant in the local (test-pinned) timezone", () => {
    // Noon in January in Los Angeles (PST, UTC-8, no DST) is 20:00 UTC.
    expect(formatChatTimestamp("2026-01-15T20:00:00Z")).toBe(
      "Jan 15, 2026, 12:00 PM",
    );
  });
});

describe("destinationDetail", () => {
  function destination(overrides: {
    platform?: "SLACK" | "TEAMS" | null;
    broken?: boolean;
    lastPostedAt?: string | null;
  }) {
    return chatDestinationSchema.parse({
      id: 1,
      name: "#guild",
      platform: "SLACK",
      maskedUrl: "hooks.slack.com/...abcd",
      broken: false,
      lastPostedAt: null,
      ...overrides,
    });
  }

  it("shows only the platform label when there is no last post", () => {
    expect(destinationDetail(destination({}))).toBe("Slack");
  });

  it("shows only the platform label when broken, even with a last post", () => {
    expect(
      destinationDetail(
        destination({ lastPostedAt: "2026-01-15T20:00:00Z", broken: true }),
      ),
    ).toBe("Slack");
  });

  it("appends the last posted time when healthy", () => {
    expect(
      destinationDetail(destination({ lastPostedAt: "2026-01-15T20:00:00Z" })),
    ).toBe("Slack, posted Jan 15, 2026, 12:00 PM");
  });

  it("falls back to Chat when the platform is null", () => {
    expect(destinationDetail(destination({ platform: null }))).toBe("Chat");
  });
});
