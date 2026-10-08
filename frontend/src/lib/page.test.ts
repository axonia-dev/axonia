import { describe, expect, it } from "vitest";
import { z } from "zod";

import { pageAfterRemoval, pageSchema } from "@/lib/page";

describe("pageSchema", () => {
  it("parses a page of items", () => {
    // given
    const schema = pageSchema(z.object({ id: z.number() }));

    // when
    const parsed = schema.parse({
      content: [{ id: 1 }],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    });

    // then
    expect(parsed.content).toHaveLength(1);
  });
});

describe("pageAfterRemoval", () => {
  it("steps back when the removed row was the last one on this page", () => {
    expect(pageAfterRemoval(3, 0)).toBe(2);
  });

  it("stays put while the page still holds rows", () => {
    expect(pageAfterRemoval(3, 1)).toBe(3);
  });

  it("stays on the first page, which has nowhere to step back to", () => {
    expect(pageAfterRemoval(0, 0)).toBe(0);
  });
});
