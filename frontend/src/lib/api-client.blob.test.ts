import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiFetchBlob } from "@/lib/api-client";

// Split out of api-client.test.ts (300-line ceiling): apiFetchBlob is the
// download-export feature's one addition to the shared client, and is
// exercised on its own here rather than folded into the JSON-response suite.

afterEach(() => {
  vi.restoreAllMocks();
});

describe("apiFetchBlob", () => {
  it("returns the body as a blob alongside the Content-Disposition filename", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("zip-bytes", {
        status: 200,
        headers: {
          "content-disposition":
            'attachment; filename="axonia-export-acme-2026-07-28.zip"',
        },
      }),
    );

    const file = await apiFetchBlob("/axonia/admin/data-export");

    expect(file.filename).toBe("axonia-export-acme-2026-07-28.zip");
    expect(file.blob).toBeInstanceOf(Blob);
    expect(file.blob.size).toBe(new Blob(["zip-bytes"]).size);
  });

  it("parses an unquoted filename too", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("zip-bytes", {
        status: 200,
        headers: { "content-disposition": "attachment; filename=export.zip" },
      }),
    );

    const file = await apiFetchBlob("/axonia/admin/data-export");

    expect(file.filename).toBe("export.zip");
  });

  it("leaves the filename undefined when the header is absent", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("zip-bytes", { status: 200 }),
    );

    const file = await apiFetchBlob("/axonia/admin/data-export");

    expect(file.filename).toBeUndefined();
  });

  it("leaves the filename undefined when the header has no filename to parse", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("zip-bytes", {
        status: 200,
        headers: { "content-disposition": "attachment" },
      }),
    );

    const file = await apiFetchBlob("/axonia/admin/data-export");

    expect(file.filename).toBeUndefined();
  });

  it("throws ApiError on a non-2xx response without reading a body as a blob", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code: "EXPORT_RATE_LIMITED" }), {
        status: 429,
        headers: { "content-type": "application/problem+json" },
      }),
    );

    const failure = await apiFetchBlob("/axonia/admin/data-export").catch(
      (e: unknown) => e,
    );

    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).status).toBe(429);
    expect((failure as ApiError).code).toBe("EXPORT_RATE_LIMITED");
  });
});
