import { describe, expect, it } from "vitest";

import { toCanonicalHtml } from "./canonical";
import cases from "./cases.json";
import { parseNote } from "./parse";

describe("closing note markdown subset", () => {
  it.each(cases)("$name", ({ input, html }) => {
    expect(toCanonicalHtml(parseNote(input))).toBe(html);
  });

  it("treats a missing note as no blocks", () => {
    expect(parseNote(null)).toEqual([]);
  });

  it("joins the paragraphs of a loose item with a break", () => {
    expect(toCanonicalHtml(parseNote("- a\n\n  b"))).toBe(
      "<ul><li>a<br>b</li></ul>",
    );
  });

  it("keeps a numbered nested list", () => {
    expect(toCanonicalHtml(parseNote("- a\n  1. b"))).toBe(
      "<ul><li>a<ol><li>b</li></ol></li></ul>",
    );
  });

  it("keeps formatting in a later numbered line that does not open a list", () => {
    expect(toCanonicalHtml(parseNote("Intro\n\n2. **second**\n3. third"))).toBe(
      "<p>Intro</p><p>2. <strong>second</strong><br>3. third</p>",
    );
  });

  it("does not turn a nested numbered line that is not a first item into a list", () => {
    expect(toCanonicalHtml(parseNote("1. a\n\n   5. b"))).toBe(
      "<ol><li>a<br>5. b</li></ol>",
    );
  });

  it("still parses two hundred marks in one run", () => {
    const markdown = "*a ".repeat(100) + "b" + " c*".repeat(100);
    expect(toCanonicalHtml(parseNote(markdown))).toMatch(/^<p><em>a <em>a /);
  });

  it("reads a run past two hundred marks as plain text, trimmed per line", () => {
    const deep = "*a ".repeat(100) + "b" + " c*".repeat(100);
    expect(
      toCanonicalHtml(parseNote(` *a\n${deep}  \n\n\t[x](https://a.com)  \n`)),
    ).toBe(`<p>*a<br>${deep}</p><p>[x](https://a.com)</p>`);
  });

  it("counts marks per run between blank lines", () => {
    const run = "**a** [b](https://b.com) _c_\n".repeat(20);
    expect(parseNote(`${run}\n${run}`)[0]?.kind).toBe("paragraph");
    expect(toCanonicalHtml(parseNote(run + run))).not.toContain("<strong>");
  });

  it.each(["snake_case_Name_2_x", "UPPER_CASE", "v2_3"])(
    "does not count an underscore inside %s",
    (word) => {
      const html = toCanonicalHtml(parseNote(`${word} `.repeat(201)));
      expect(html).toContain(word);
    },
  );

  it.each(["_a", "a_", "a _b", "a_ b", "{_a", "]"])(
    "counts an underscore or bracket at a word edge in %s",
    (text) => {
      const markdown = "**x** " + `${text} `.repeat(200) + text;
      expect(toCanonicalHtml(parseNote(markdown))).toMatch(/^<p>\*\*x\*\*/);
    },
  );

  it.each([
    ["- ".repeat(32) + "**x**", false],
    ["- ".repeat(33) + "**x**", true],
    [" ".repeat(64) + "**x**", false],
    [" ".repeat(66) + "**x**", true],
    ["\t".repeat(16) + " 1. **x**", true],
    ["  \t".repeat(16) + "**x**", false],
  ])("reads deep list nesting as plain text (%#)", (markdown, plain) => {
    expect(toCanonicalHtml(parseNote(markdown)).includes("**")).toBe(plain);
  });

  it("ends a url found after an open bracket at the end of its paragraph", () => {
    expect(toCanonicalHtml(parseNote("[x https://a.com/y_"))).toBe(
      '<p>[x <a href="https://a.com/y">https://a.com/y</a>_</p>',
    );
  });
});
