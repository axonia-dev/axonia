import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpRoundupGateway } from "@/features/admin/adapters/http-roundup-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

describe("httpRoundupGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("getSettings reads the organization's roundup setting", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ organizationId: "acme", enabled: true }),
    );

    const settings = await httpRoundupGateway.getSettings();

    expect(settings).toEqual({ organizationId: "acme", enabled: true });
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/roundup/settings");
  });

  it("setEnabled PUTs the flag and parses the updated settings", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ organizationId: "acme", enabled: true }),
    );

    const settings = await httpRoundupGateway.setEnabled(true);

    expect(settings).toEqual({ organizationId: "acme", enabled: true });
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/roundup/settings");
    expect(init?.method).toBe("PUT");
    expect(typeof init?.body === "string" ? init.body : "").toBe(
      JSON.stringify({ enabled: true }),
    );
  });

  it("setEnabled leaves the content type to the api client, so it cannot double up", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ organizationId: "acme", enabled: false }),
    );

    await httpRoundupGateway.setEnabled(false);

    const [, init] = fetchSpy.mock.calls[0] ?? [];
    expect(new Headers(init?.headers).get("content-type")).toBe(
      "application/json",
    );
  });

  it("getPreview reads the rendered message for the closed quarter", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ subject: "Acme, last quarter", html: "<p>hi</p>" }),
    );

    const preview = await httpRoundupGateway.getPreview();

    expect(preview).toEqual({
      subject: "Acme, last quarter",
      html: "<p>hi</p>",
    });
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/roundup/preview");
  });

  it("rejects a settings payload that is not the shape the backend promises", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ organizationId: "acme" }));

    await expect(httpRoundupGateway.getSettings()).rejects.toThrow();
  });
});
