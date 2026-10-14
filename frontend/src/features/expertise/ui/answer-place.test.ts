import { describe, expect, it } from "vitest";

import { answerPlace } from "@/features/expertise/ui/answer-place";

describe("answerPlace", () => {
  it("answers in the panel beside the map, in place of a chosen area's people", () => {
    expect(
      answerPlace({
        layout: "beside",
        matchedAreas: ["kafka"],
        selectedTag: "rust",
      }),
    ).toEqual({ inPanel: ["kafka"], underDrawing: [], panelGivesWay: true });
  });

  it("answers under the drawing with the readings under the map, and the panel gives way", () => {
    expect(
      answerPlace({
        layout: "under",
        matchedAreas: ["kafka"],
        selectedTag: "kafka",
      }),
    ).toEqual({ inPanel: [], underDrawing: ["kafka"], panelGivesWay: true });
  });

  it("leaves the readings above the box alone, and never repeats the chosen area's people", () => {
    expect(
      answerPlace({
        layout: "above",
        matchedAreas: ["kafka"],
        selectedTag: "kafka",
      }),
    ).toEqual({ inPanel: [], underDrawing: [], panelGivesWay: false });
    expect(
      answerPlace({
        layout: "above",
        matchedAreas: ["kafka"],
        selectedTag: "rust",
      }),
    ).toEqual({ inPanel: [], underDrawing: ["kafka"], panelGivesWay: false });
  });

  it("gives way to nothing while no term matches", () => {
    expect(
      answerPlace({ layout: "under", matchedAreas: [], selectedTag: "kafka" }),
    ).toEqual({ inPanel: [], underDrawing: [], panelGivesWay: false });
  });
});
