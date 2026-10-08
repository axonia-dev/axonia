import { describe, expect, it } from "vitest";

import { wrapName } from "@/features/expertise/ui/shape-name-wrap";

const perChar = (width: number) => (text: string) => text.length * width;
const six = perChar(6);

describe("wrapName", () => {
  it("keeps a name that fits on one line", () => {
    expect(wrapName("AROUND KAFKA", 170, 0, six)).toEqual({
      lines: ["AROUND KAFKA"],
      marksOwnLine: false,
      cut: false,
    });
  });

  it("wraps at spaces, at most three lines", () => {
    expect(
      wrapName("Developer Experience and Platform Tooling", 100, 0, six).lines,
    ).toEqual(["Developer", "Experience and", "Platform Tooling"]);
  });

  it("breaks after a hyphen when a tag has no space", () => {
    expect(wrapName("infrastructure-as-code", 100, 0, six).lines).toEqual([
      "infrastructure-",
      "as-code",
    ]);
    expect(wrapName("AROUND THREAT-MODELING", 60, 0, six).lines).toEqual([
      "AROUND",
      "THREAT-",
      "MODELING",
    ]);
  });

  it("ends the last line on an ellipsis when the name needs more than three lines", () => {
    const wrapped = wrapName(
      "Developer Experience and Platform Tooling Guild",
      95,
      0,
      six,
    );
    expect(wrapped.lines).toEqual([
      "Developer",
      "Experience and",
      "Platform Tooli…",
    ]);
    expect(wrapped.cut).toBe(true);
  });

  it("cuts only a word wider than a whole line", () => {
    const wrapped = wrapName("Supercalifragilisticexpialidocious", 100, 0, six);
    expect(wrapped.lines).toHaveLength(1);
    expect(wrapped.lines[0]).toMatch(/^Supercalifrag.*…$/u);
    expect(wrapped.cut).toBe(true);
  });

  it("wraps by the widths it is handed", () => {
    expect(wrapName("AROUND KAFKA", 60, 0, perChar(5)).lines).toEqual([
      "AROUND KAFKA",
    ]);
    expect(wrapName("AROUND KAFKA", 60, 0, perChar(8)).lines).toEqual([
      "AROUND",
      "KAFKA",
    ]);
  });

  it("keeps the marks beside the last line where they fit", () => {
    expect(wrapName("AROUND KAFKA", 100, 20, six)).toEqual({
      lines: ["AROUND KAFKA"],
      marksOwnLine: false,
      cut: false,
    });
  });

  it("moves the last word down beside the marks rather than dropping them to a line of their own", () => {
    expect(wrapName("AROUND KAFKA", 80, 20, six)).toEqual({
      lines: ["AROUND", "KAFKA"],
      marksOwnLine: false,
      cut: false,
    });
  });

  it("puts the marks on a line of their own rather than cutting a word for them", () => {
    expect(wrapName("OBSERVABILITY", 80, 32, six)).toEqual({
      lines: ["OBSERVABILITY"],
      marksOwnLine: true,
      cut: false,
    });
  });

  it("has no line for an empty name", () => {
    expect(wrapName("", 100, 0, six)).toEqual({
      lines: [],
      marksOwnLine: false,
      cut: false,
    });
  });
});
