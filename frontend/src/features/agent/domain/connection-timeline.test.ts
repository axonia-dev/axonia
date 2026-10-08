import { describe, expect, it } from "vitest";

import type { AgentConnection } from "./agent-connection";
import { connectionTimeline } from "./connection-timeline";

const NOW = new Date("2026-09-24T12:00:00Z");
const MINUTE_MS = 60 * 1000;
const DAY_MS = 24 * 60 * MINUTE_MS;

function connection(overrides: Partial<AgentConnection> = {}): AgentConnection {
  return {
    id: 1,
    clientName: "Claude Code",
    clientHost: "claude.ai",
    scopes: ["read"],
    activatedAt: "2026-09-20T12:00:00Z",
    lastUsedAt: null,
    endsAt: "2027-03-19T12:00:00Z",
    status: "ACTIVE",
    ...overrides,
  };
}

describe("connectionTimeline", () => {
  it("says when it connected, was used and ends", () => {
    const timeline = connectionTimeline(
      connection({ lastUsedAt: "2026-09-24T10:00:00Z" }),
      NOW,
    );

    expect(timeline.connected).toBe("Connected Sep 20, 2026");
    expect(timeline.lastUsed).toMatch(/^Used /);
    expect(timeline.ends).toMatch(/^Ends /);
  });

  it("reads a fresh 180-day connection as ending in 6 months", () => {
    const activatedAt = new Date(NOW.getTime() - MINUTE_MS);
    const endsAt = new Date(activatedAt.getTime() + 180 * DAY_MS);

    const timeline = connectionTimeline(
      connection({
        activatedAt: activatedAt.toISOString(),
        endsAt: endsAt.toISOString(),
      }),
      NOW,
    );

    expect(timeline.ends).toBe("Ends in 6 months");
  });

  it("rounds 45 days up to 2 months", () => {
    const endsAt = new Date(NOW.getTime() + 45 * DAY_MS);

    const timeline = connectionTimeline(
      connection({ endsAt: endsAt.toISOString() }),
      NOW,
    );

    expect(timeline.ends).toBe("Ends in 2 months");
  });

  it("rounds 40 days down to 1 month", () => {
    const endsAt = new Date(NOW.getTime() + 40 * DAY_MS);

    const timeline = connectionTimeline(
      connection({ endsAt: endsAt.toISOString() }),
      NOW,
    );

    expect(timeline.ends).toBe("Ends in 1 month");
  });

  it("says never used", () => {
    expect(connectionTimeline(connection(), NOW).lastUsed).toBe("Never used");
  });
});
