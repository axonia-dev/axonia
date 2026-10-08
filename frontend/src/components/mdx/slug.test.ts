import { describe, expect, it } from "vitest";

import { slugify } from "./slug";

describe("slugify", () => {
  it("lowercases and hyphenates", () => {
    expect(slugify("Configure your client")).toBe("configure-your-client");
  });

  it("strips leading and trailing separators", () => {
    expect(slugify("  1. Mint an API key!  ")).toBe("1-mint-an-api-key");
  });
});
