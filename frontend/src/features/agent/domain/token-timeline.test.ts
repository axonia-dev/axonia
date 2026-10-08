import { describe, expect, it } from "vitest";

import type { PersonalToken } from "./personal-access-token";
import { tokenTimeline } from "./token-timeline";

const NOW = new Date("2026-08-14T18:00:00Z");

function token(overrides: Partial<PersonalToken> = {}): PersonalToken {
  return {
    id: 1,
    name: "claude",
    scopes: ["read"],
    createdAt: "2026-08-14T18:00:00Z",
    lastUsedAt: null,
    expiresAt: null,
    status: "ACTIVE",
    ...overrides,
  };
}

describe("tokenTimeline", () => {
  it("states creation as a date, because it identifies the key rather than its health", () => {
    expect(tokenTimeline(token(), NOW).created).toBe("Created Aug 14, 2026");
  });

  it("says a key has never been used", () => {
    expect(tokenTimeline(token(), NOW).lastUsed).toBe("Never used");
  });

  it("states last use relatively: the question is whether the key is still in use", () => {
    const timeline = tokenTimeline(
      token({ lastUsedAt: "2026-08-14T16:00:00Z" }),
      NOW,
    );
    expect(timeline.lastUsed).toBe("Last used 2 hours ago");
  });

  it("says a key never expires", () => {
    expect(tokenTimeline(token(), NOW).expiry).toBe("No expiry");
  });

  it("states a future expiry relatively", () => {
    const timeline = tokenTimeline(
      token({ expiresAt: "2026-08-16T18:00:00Z" }),
      NOW,
    );
    expect(timeline.expiry).toBe("Expires in 2 days");
    expect(timeline.expiringSoon).toBe(true);
  });

  it("does not warn about an expiry more than seven days out", () => {
    const timeline = tokenTimeline(
      token({ expiresAt: "2026-09-30T18:00:00Z" }),
      NOW,
    );
    expect(timeline.expiringSoon).toBe(false);
  });

  it("states a lapsed expiry as the date it happened", () => {
    const timeline = tokenTimeline(
      token({ status: "EXPIRED", expiresAt: "2026-06-30T18:00:00Z" }),
      NOW,
    );
    expect(timeline.expiry).toBe("Expired Jun 30, 2026");
    expect(timeline.expiringSoon).toBe(false);
  });

  it("never warns about a revoked key, however near its expiry", () => {
    const timeline = tokenTimeline(
      token({ status: "REVOKED", expiresAt: "2026-08-16T18:00:00Z" }),
      NOW,
    );
    expect(timeline.expiringSoon).toBe(false);
  });

  it("states a revoked key's lapsed expiry in the past tense, not the future", () => {
    const timeline = tokenTimeline(
      token({ status: "REVOKED", expiresAt: "2026-06-30T18:00:00Z" }),
      NOW,
    );
    expect(timeline.expiry).toBe("Expired Jun 30, 2026");
  });
});
