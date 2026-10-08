import { describe, expect, it } from "vitest";

import { DOC_PAGES, findDocPage } from "./doc-registry";

describe("doc registry", () => {
  it("lists the seed pages in order", () => {
    expect(DOC_PAGES.map((p) => p.slug)).toEqual([
      "agent-overview",
      "connect-your-agent",
    ]);
  });

  it("has unique slugs", () => {
    const slugs = DOC_PAGES.map((p) => p.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
  });

  it("finds a page by slug", () => {
    expect(findDocPage("connect-your-agent")?.title).toBe("Connect an agent");
  });

  it("returns undefined for an unknown slug", () => {
    expect(findDocPage("nope")).toBeUndefined();
  });
});
