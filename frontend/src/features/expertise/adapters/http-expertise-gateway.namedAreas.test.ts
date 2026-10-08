import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const BASE = "/axonia/communities/guild/expertise/curation/named-areas";

function voidResponse(): Response {
  return new Response(null, { status: 204 });
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) return input.url;
  return input instanceof URL ? input.href : (input ?? "");
}

function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}

function methodOf(init: RequestInit | undefined): string {
  return init?.method ?? "GET";
}

describe("httpExpertiseGateway - named areas", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  function lastCall() {
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    return { url: urlOf(url), method: methodOf(init), body: bodyOf(init) };
  }

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("reads every named area with its stored tags", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse([{ id: 7, name: "Streaming", tags: ["kafka", "flink"] }]),
    );

    // when
    const namedAreas = await httpExpertiseGateway.namedAreas("guild");

    // then
    expect(namedAreas).toEqual([
      { id: 7, name: "Streaming", tags: ["kafka", "flink"] },
    ]);
    expect(lastCall().url).toContain(BASE);
    expect(lastCall().method).toBe("GET");
  });

  it("creates a named area and returns it", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({ id: 8, name: "Data", tags: [] }, 201),
    );

    // when
    const created = await httpExpertiseGateway.createNamedArea("guild", "Data");

    // then
    expect(created).toEqual({ id: 8, name: "Data", tags: [] });
    expect(lastCall()).toEqual({
      url: expect.stringContaining(BASE) as string,
      method: "POST",
      body: JSON.stringify({ name: "Data" }),
    });
  });

  it("renames a named area", async () => {
    // given
    fetchSpy.mockResolvedValue(voidResponse());

    // when
    const result = await httpExpertiseGateway.renameNamedArea(
      "guild",
      8,
      "Storage",
    );

    // then
    expect(result).toBeUndefined();
    expect(lastCall()).toEqual({
      url: expect.stringContaining(`${BASE}/8/rename`) as string,
      method: "POST",
      body: JSON.stringify({ name: "Storage" }),
    });
  });

  it("deletes a named area", async () => {
    // given
    fetchSpy.mockResolvedValue(voidResponse());

    // when
    await httpExpertiseGateway.deleteNamedArea("guild", 8);

    // then
    expect(lastCall().url).toContain(`${BASE}/8/delete`);
    expect(lastCall().method).toBe("POST");
  });

  it("places a tag in a named area", async () => {
    // given
    fetchSpy.mockResolvedValue(voidResponse());

    // when
    await httpExpertiseGateway.placeInNamedArea("guild", 8, "postgres");

    // then
    expect(lastCall()).toEqual({
      url: expect.stringContaining(`${BASE}/8/tags`) as string,
      method: "POST",
      body: JSON.stringify({ tag: "postgres" }),
    });
  });

  it("removes a tag from whichever named area holds it", async () => {
    // given
    fetchSpy.mockResolvedValue(voidResponse());

    // when
    await httpExpertiseGateway.removeFromNamedArea("guild", "c#");

    // then
    expect(lastCall().url).toContain(`${BASE}/tags/c%23/remove`);
    expect(lastCall().method).toBe("POST");
  });
});
