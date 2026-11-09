import { describe, expect, it } from "vitest";

import { contentMaxWidthClass } from "@/features/community/ui/content-width";

describe("contentMaxWidthClass", () => {
  it("widens the schedule page", () => {
    expect(contentMaxWidthClass("/communities/backend/schedule")).toBe(
      "max-w-[2048px]",
    );
  });

  // At the reading width the map's own column came out under 400px on every screen size, because the 296px
  // readings panel sits beside it inside the same 768px.
  it("widens the expertise page, which draws a territory rather than reading a list", () => {
    expect(contentMaxWidthClass("/communities/backend/expertise")).toBe(
      "max-w-[2048px]",
    );
  });

  it("keeps the reading width for the member's own expertise page, a list like any other", () => {
    expect(contentMaxWidthClass("/communities/backend/me/expertise")).toBe(
      "max-w-3xl",
    );
  });

  it("widens a wide page whatever its trailing slash", () => {
    expect(contentMaxWidthClass("/communities/backend/expertise/")).toBe(
      "max-w-[2048px]",
    );
  });

  // The run sheet sits beside its 13rem rail, so at the reading width its slot cards came out
  // around 500px wide and left most of a large screen empty.
  it("gives a session page the session width, between reading and drawing", () => {
    expect(contentMaxWidthClass("/communities/backend/sessions/42")).toBe(
      "max-w-6xl",
    );
  });

  // A glance row of four tiles and two panels side by side, each with a chart, do not fit 768px.
  it("gives the health page the dashboard width", () => {
    expect(contentMaxWidthClass("/communities/backend/health")).toBe(
      "max-w-5xl",
    );
  });

  it("keeps the reading width for other community pages", () => {
    expect(contentMaxWidthClass("/communities/backend")).toBe("max-w-3xl");
    expect(contentMaxWidthClass("/communities/backend/backlog")).toBe(
      "max-w-3xl",
    );
  });
});
