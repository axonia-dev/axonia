import { describe, expect, it } from "vitest";

import { provenanceText } from "@/features/knowledge/ui/provenance-text";

const gone = { seriesTitle: null, heldOn: null };

describe("provenanceText", () => {
  it("drops the presenter suffix from a legacy, em-dash-separated snapshot", () => {
    expect(
      // eslint-disable-next-line no-restricted-syntax -- exercises the legacy em-dash-separated label format
      provenanceText({ label: "Outbox pattern \u2014 Lee", ...gone }),
    ).toBe("Outbox pattern");
  });

  it("keeps a hyphenated title whole: the hyphen is part of the title, not a separator", () => {
    // The regression this pins: SlotRow used to append " - <presenters>", and splitting a new
    // label on " - " truncated "Kafka - the good parts" to "Kafka". Titles of this shape are common.
    expect(provenanceText({ label: "Kafka - the good parts", ...gone })).toBe(
      "Kafka - the good parts",
    );
  });

  it("keeps a presenter-less label as the topic", () => {
    expect(provenanceText({ label: "Idempotency", ...gone })).toBe(
      "Idempotency",
    );
  });

  it("falls back to a generic topic for an empty label", () => {
    expect(provenanceText({ label: null, ...gone })).toBe("a session");
  });

  it("prefixes the series and appends the day the session was held on", () => {
    expect(
      provenanceText({
        label: "Outbox pattern",
        seriesTitle: "Patterns in Practice",
        heldOn: "2026-06-09",
      }),
    ).toBe("Patterns in Practice · Outbox pattern (Jun 9, 2026)");
  });

  it("appends the date without a series for a one-off session", () => {
    expect(
      provenanceText({
        label: "Lightning talk",
        seriesTitle: null,
        heldOn: "2026-06-09",
      }),
    ).toBe("Lightning talk (Jun 9, 2026)");
  });
});
