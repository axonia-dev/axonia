import { describe, expect, it } from "vitest";

import {
  emptyTemplate,
  totalMinutes,
} from "@/features/events/domain/structure";

describe("structure helpers", () => {
  it("emptyTemplate seeds a sectionTypeId with null title/duration", () => {
    expect(emptyTemplate(2)).toEqual({
      sectionTypeId: 2,
      title: null,
      durationMinutes: null,
    });
  });

  it("totalMinutes sums durations, treating null as 0", () => {
    expect(
      totalMinutes([
        { sectionTypeId: 1, title: null, durationMinutes: 30 },
        emptyTemplate(2),
      ]),
    ).toBe(30);
  });
});
