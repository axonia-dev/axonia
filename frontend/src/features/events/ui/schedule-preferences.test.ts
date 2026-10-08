import { beforeEach, describe, expect, it } from "vitest";

import {
  parseView,
  storedMonth,
  storedView,
  storeMonth,
  storeView,
} from "@/features/events/ui/schedule-preferences";

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe("parsing a view value", () => {
  it("reads a recognised view", () => {
    expect(parseView("timeline")).toBe("timeline");
    expect(parseView("calendar")).toBe("calendar");
  });

  it("ignores anything else, including null", () => {
    expect(parseView(null)).toBeNull();
    expect(parseView("gantt")).toBeNull();
    expect(parseView("")).toBeNull();
  });
});

describe("the view preference", () => {
  it("survives in localStorage, since a preferred view is preferred tomorrow", () => {
    storeView("timeline");
    expect(storedView()).toBe("timeline");
    expect(localStorage.getItem("axonia-schedule-view")).toBe("timeline");
  });

  it("answers nothing when unset or unrecognised", () => {
    expect(storedView()).toBeNull();
    localStorage.setItem("axonia-schedule-view", "gantt");
    expect(storedView()).toBeNull();
  });
});

describe("the month position", () => {
  // A position, not a preference: it expires with the tab rather than opening
  // next week on a month chosen last week.
  it("is kept in sessionStorage, not localStorage", () => {
    storeMonth("backend-guild", { year: 2026, monthIndex0: 7 });
    expect(sessionStorage.getItem("axonia-schedule-month:backend-guild")).toBe(
      "2026-08",
    );
    expect(localStorage).toHaveLength(0);
  });

  it("reads a stored position back", () => {
    storeMonth("backend-guild", { year: 2026, monthIndex0: 7 });
    expect(storedMonth("backend-guild")).toEqual({
      year: 2026,
      monthIndex0: 7,
    });
  });

  it("never carries one community's month into another", () => {
    storeMonth("backend-guild", { year: 2026, monthIndex0: 7 });
    expect(storedMonth("frontend-guild")).toBeNull();
  });

  it("answers nothing when the stored value is unreadable", () => {
    sessionStorage.setItem("axonia-schedule-month:backend-guild", "later");
    expect(storedMonth("backend-guild")).toBeNull();
  });
});
