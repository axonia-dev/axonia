import { readFileSync } from "node:fs";
import { join } from "node:path";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SRC, withoutComments } from "@/test/source-files";

import {
  AGENT_SCOPES,
  createTokenBodySchema,
  knownScopes,
  scopeLabel,
  unknownScopes,
} from "./personal-access-token";

const AGENT_SCOPE_ENUM = join(
  SRC,
  "../../backend/core/src/main/java/dev/axonia/agent/AgentScope.java",
);

function sorted(codes: readonly string[]): string[] {
  return codes.slice().sort((a, b) => a.localeCompare(b));
}

/** Every code the backend enum declares. The only string literals in that file are the codes. */
function backendScopeCodes(): string[] {
  // Java parsed as TypeScript: it lexes close enough for its comments, and a text block would fail loudly.
  const source = withoutComments(
    readFileSync(AGENT_SCOPE_ENUM, "utf8"),
    AGENT_SCOPE_ENUM,
  );
  return [...source.matchAll(/"([^"]+)"/g)].map((match) => match[1] ?? "");
}

describe("AGENT_SCOPES", () => {
  /**
   * A scope the backend grants but this catalog omits cannot be granted through the UI at all, so
   * the tool behind it is unreachable. The backend enum is read rather than transcribed: a copied
   * list is only ever as current as the hand that copied it, and cannot fail when the other side
   * gains a scope.
   */
  it("mirrors the backend scope catalog, code for code", () => {
    const catalog = AGENT_SCOPES.map((scope) => scope.code);

    expect(sorted(catalog)).toEqual(sorted(backendScopeCodes()));
  });

  it("names every scope it offers", () => {
    for (const scope of AGENT_SCOPES) {
      expect(scope.label).not.toBe("");
    }
  });
});

describe("scopeLabel", () => {
  it("names a scope in the catalog", () => {
    expect(scopeLabel("members:read")).toBe(
      "Read community member lists and learners",
    );
  });

  it("falls back to the raw code for a scope the catalog does not know", () => {
    expect(scopeLabel("future:capability")).toBe("future:capability");
  });
});

describe("knownScopes", () => {
  it("keeps the codes the catalog knows, in catalog order", () => {
    expect(knownScopes(["topic:propose", "read"])).toEqual([
      "read",
      "topic:propose",
    ]);
  });

  it("drops a code the UI could not render", () => {
    expect(knownScopes(["read", "future:capability"])).toEqual(["read"]);
  });
});

describe("unknownScopes", () => {
  it("keeps only the codes the catalog does not recognise", () => {
    expect(unknownScopes(["read", "future:capability"])).toEqual([
      "future:capability",
    ]);
  });

  it("returns nothing when every code is known", () => {
    expect(unknownScopes(["read", "topic:propose"])).toEqual([]);
  });
});

describe("createTokenBodySchema", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("accepts a name, a scope, and a future expiry", () => {
    const result = createTokenBodySchema.safeParse({
      name: "cli",
      scopes: ["read"],
      expiresAt: "2027-01-01T23:59:59Z",
    });
    expect(result.success).toBe(true);
  });

  it("reads a blank expiry as absent, so the token lasts until it is revoked", () => {
    const result = createTokenBodySchema.safeParse({
      name: "cli",
      scopes: ["read"],
      expiresAt: "",
    });
    expect(result.success).toBe(true);
    expect(result.data?.expiresAt).toBeNull();
  });

  it("accepts an omitted expiry", () => {
    const result = createTokenBodySchema.safeParse({
      name: "cli",
      scopes: ["read"],
    });
    expect(result.success).toBe(true);
  });

  it("refuses a past expiry, naming the field", () => {
    const result = createTokenBodySchema.safeParse({
      name: "cli",
      scopes: ["read"],
      expiresAt: "2020-01-01T23:59:59Z",
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe(
      "Expiry date must be in the future",
    );
  });
});
