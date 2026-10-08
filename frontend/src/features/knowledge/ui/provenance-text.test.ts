import { describe, expect, it } from "vitest";

import type { Session } from "@/features/events/domain/session";
import { provenanceText } from "@/features/knowledge/ui/provenance-text";

const session = {
  occurrenceDate: "2026-06-09",
  scheduledAt: "2026-06-09T18:00:00Z",
} as Session;

describe("provenanceText", () => {
  it("drops the presenter suffix from a legacy, em-dash-separated snapshot", () => {
    expect(
      // eslint-disable-next-line no-restricted-syntax -- exercises the legacy em-dash-separated label format
      provenanceText("Outbox pattern \u2014 Lee", undefined, undefined),
    ).toBe("Outbox pattern");
  });

  it("keeps a hyphenated title whole: the hyphen is part of the title, not a separator", () => {
    // The regression this pins: SlotRow used to append " - <presenters>", and splitting a new
    // label on " - " truncated "Kafka - the good parts" to "Kafka". Titles of this shape are common.
    expect(provenanceText("Kafka - the good parts", undefined, undefined)).toBe(
      "Kafka - the good parts",
    );
  });

  it("keeps a presenter-less label as the topic", () => {
    expect(provenanceText("Idempotency", undefined, undefined)).toBe(
      "Idempotency",
    );
  });

  it("falls back to a generic topic for an empty label", () => {
    expect(provenanceText(null, undefined, undefined)).toBe("a session");
  });

  it("prefixes the series and appends the live date when resolved", () => {
    expect(
      provenanceText("Outbox pattern", session, "Patterns in Practice"),
    ).toBe("Patterns in Practice · Outbox pattern (Jun 9, 2026)");
  });

  it("appends the date without a series for a one-off session", () => {
    expect(provenanceText("Lightning talk", session, undefined)).toBe(
      "Lightning talk (Jun 9, 2026)",
    );
  });
});
