import { describe, expect, it } from "vitest";

import {
  MESSAGE_TYPES,
  preferenceFor,
  withPreference,
} from "@/features/communication/domain/notification-preference";

describe("MESSAGE_TYPES", () => {
  it("offers a switch for every message type the backend can send, roundup included", () => {
    expect(MESSAGE_TYPES.map((copy) => copy.type)).toEqual([
      "session_upcoming",
      "session_wrap_up",
      "session_awaiting_close",
      "organization_roundup",
    ]);
  });
});

describe("preferenceFor", () => {
  it("reads the stored value for a known type", () => {
    const preferences = [{ type: "session_upcoming" as const, enabled: false }];
    expect(preferenceFor(preferences, "session_upcoming")).toBe(false);
  });

  it("defaults a missing type to enabled", () => {
    expect(preferenceFor([], "session_wrap_up")).toBe(true);
  });
});

describe("withPreference", () => {
  it("replaces the row for the given type, leaving the others untouched", () => {
    const preferences = [
      { type: "session_upcoming" as const, enabled: true },
      { type: "session_wrap_up" as const, enabled: true },
    ];

    const next = withPreference(preferences, "session_wrap_up", false);

    expect(next).toEqual([
      { type: "session_upcoming", enabled: true },
      { type: "session_wrap_up", enabled: false },
    ]);
  });

  it("preserves row order when updating a row in the middle", () => {
    const preferences = [
      { type: "session_upcoming" as const, enabled: true },
      { type: "session_wrap_up" as const, enabled: true },
      { type: "session_awaiting_close" as const, enabled: true },
    ];

    const next = withPreference(preferences, "session_wrap_up", false);

    expect(next.map((preference) => preference.type)).toEqual([
      "session_upcoming",
      "session_wrap_up",
      "session_awaiting_close",
    ]);
  });

  it("inserts a row for a type that was not present", () => {
    const next = withPreference([], "session_awaiting_close", false);
    expect(next).toEqual([{ type: "session_awaiting_close", enabled: false }]);
  });
});
