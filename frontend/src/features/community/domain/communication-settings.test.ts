import { describe, expect, it } from "vitest";

import {
  communicationFieldError,
  communicationSettingsSchema,
  parseCommunicationSettingsInput,
} from "@/features/community/domain/communication-settings";

describe("communicationSettingsSchema", () => {
  it("parses a valid settings payload", () => {
    // given
    const raw = {
      notifySessionUpcoming: true,
      notifySessionWrapUp: false,
      sessionUpcomingLeadDays: 5,
      wrapUpGraceHours: 72,
    };

    // when
    const actual = communicationSettingsSchema.parse(raw);

    // then
    expect(actual).toEqual(raw);
  });

  it("rejects a payload with a missing field", () => {
    // given
    const raw = { notifySessionUpcoming: true };

    // when
    const actual = communicationSettingsSchema.safeParse(raw);

    // then
    expect(actual.success).toBe(false);
  });
});

describe("parseCommunicationSettingsInput", () => {
  const valid = {
    notifySessionUpcoming: true,
    notifySessionWrapUp: false,
    sessionUpcomingLeadDays: "3",
    wrapUpGraceHours: "48",
  };

  it("parses valid string fields into a settings record", () => {
    // when
    const actual = parseCommunicationSettingsInput(valid);

    // then
    expect(actual).toEqual({
      notifySessionUpcoming: true,
      notifySessionWrapUp: false,
      sessionUpcomingLeadDays: 3,
      wrapUpGraceHours: 48,
    });
  });

  it("accepts the exact backend bounds: 0 and 30 lead days, 0 and 336 grace hours", () => {
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "0",
        wrapUpGraceHours: "0",
      }),
    ).not.toBeNull();
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "30",
        wrapUpGraceHours: "336",
      }),
    ).not.toBeNull();
  });

  it("rejects one past the backend bounds", () => {
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "31",
      }),
    ).toBeNull();
    expect(
      parseCommunicationSettingsInput({ ...valid, wrapUpGraceHours: "337" }),
    ).toBeNull();
  });

  it("rejects a negative value", () => {
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "-1",
      }),
    ).toBeNull();
  });

  it("rejects an empty, non-numeric, or fractional field", () => {
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "",
      }),
    ).toBeNull();
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "abc",
      }),
    ).toBeNull();
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "2.5",
      }),
    ).toBeNull();
  });

  it("rejects JS numeric-literal forms a plain digit string never contains", () => {
    // hex ("0x10" -> 16) and exponential ("1e1" -> 10) notation would otherwise
    // let a saved value silently differ from what was typed
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        sessionUpcomingLeadDays: "0x10",
      }),
    ).toBeNull();
    expect(
      parseCommunicationSettingsInput({
        ...valid,
        wrapUpGraceHours: "1e1",
      }),
    ).toBeNull();
  });
});

describe("communicationFieldError", () => {
  it("is undefined for a value inside the bound", () => {
    expect(communicationFieldError("30", 30)).toBeUndefined();
    expect(communicationFieldError("0", 30)).toBeUndefined();
  });

  it("names the exact bound for a value outside it", () => {
    expect(communicationFieldError("31", 30)).toBe(
      "Enter a whole number from 0 to 30.",
    );
  });

  it("names the exact bound for fractional, negative, or non-numeric input", () => {
    expect(communicationFieldError("2.5", 336)).toBe(
      "Enter a whole number from 0 to 336.",
    );
    expect(communicationFieldError("-1", 336)).toBe(
      "Enter a whole number from 0 to 336.",
    );
    expect(communicationFieldError("abc", 336)).toBe(
      "Enter a whole number from 0 to 336.",
    );
  });

  it("stays quiet on a blank field - Save disabling is enough while it's just been cleared", () => {
    // A field cleared to be retyped must not flash an error for every
    // keystroke of the pause between clearing and typing the next value.
    expect(communicationFieldError("", 336)).toBeUndefined();
    expect(communicationFieldError("   ", 336)).toBeUndefined();
  });
});
