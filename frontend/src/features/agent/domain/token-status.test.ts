import { describe, expect, it } from "vitest";

import { tokenStatusView } from "./token-status";

describe("tokenStatusView", () => {
  it("gives an active key the success tone", () => {
    expect(tokenStatusView("ACTIVE")).toEqual({
      tone: "success",
      label: "Active",
    });
  });

  it("gives a revoked key the danger tone", () => {
    expect(tokenStatusView("REVOKED")).toEqual({
      tone: "danger",
      label: "Revoked",
    });
  });

  it("gives an expired key a neutral tone: a lapse on schedule is not an alarm", () => {
    expect(tokenStatusView("EXPIRED")).toEqual({
      tone: "neutral",
      label: "Expired",
    });
  });
});
