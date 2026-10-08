import { afterEach, describe, expect, it, vi } from "vitest";

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("recordOpening", () => {
  it("posts to the artifact's openings path", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(jsonResponse(null, 204));

    await httpKnowledgeGateway.recordOpening(42);

    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toContain("/axonia/artifacts/42/openings");
    expect(init?.method).toBe("POST");
  });

  // apiFetchVoid always sets its own Content-Type internally, even for a bodyless
  // request; what this guards against is the call site ALSO passing one. A same-cased
  // duplicate key collapses in the object spread, but a differently-cased one
  // ("Content-Type" beside "content-type") survives it, and Headers.append (not
  // replace) would then read back "application/json, application/json" and the
  // backend 415s. See the same pattern in http-personal-access-token-gateway.test.ts.
  it("sends Content-Type exactly once, with no doubling from the call site", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(jsonResponse(null, 204));

    await httpKnowledgeGateway.recordOpening(42);

    const [, init] = fetchSpy.mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get("content-type")).toBe("application/json");
  });
});
