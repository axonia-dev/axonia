import { describe, expect, it } from "vitest";

import { plural, pluralWord } from "@/lib/plural";

describe("plural", () => {
  it("pluralises a count of zero", () => {
    expect(plural(0, "member")).toBe("0 members");
  });

  it("keeps the singular form for exactly one", () => {
    expect(plural(1, "member")).toBe("1 member");
  });

  it("pluralises a count greater than one", () => {
    expect(plural(5, "member")).toBe("5 members");
  });

  it("uses the irregular plural for person", () => {
    expect(plural(5, "person")).toBe("5 people");
    expect(plural(1, "person")).toBe("1 person");
  });
});

describe("pluralWord", () => {
  it("gives the bare noun so a label can sit under its own number", () => {
    expect(pluralWord(1, "team")).toBe("team");
    expect(pluralWord(6, "team")).toBe("teams");
    expect(pluralWord(0, "business unit")).toBe("business units");
    expect(pluralWord(1, "person")).toBe("person");
    expect(pluralWord(3, "person")).toBe("people");
  });
});

describe("pluralWord rules shared with the other app", () => {
  it("turns a consonant and y into ies, keeping the case", () => {
    // when
    const actual = [pluralWord(2, "community"), pluralWord(2, "Community")];

    // then
    expect(actual).toEqual(["communities", "Communities"]);
  });

  it("adds an s after a vowel and y", () => {
    // when
    const actual = pluralWord(2, "community-day");

    // then
    expect(actual).toBe("community-days");
  });

  it("finds an irregular plural whatever its case, at the end of a phrase", () => {
    // when
    const actual = [
      pluralWord(2, "Person"),
      pluralWord(2, "active person"),
      pluralWord(2, "Active member"),
    ];

    // then
    expect(actual).toEqual(["People", "active people", "Active members"]);
  });

  it("never answers with an object property for a noun that shares its name", () => {
    // when
    const actual = pluralWord(2, "constructor");

    // then
    expect(actual).toBe("constructors");
  });
});
