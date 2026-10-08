import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("httpEventsGateway: section-type methods", () => {
  it("listSectionTypes GETs /axonia/section-types and validates the response", async () => {
    const sectionType = {
      id: 1,
      label: "Talk",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    };
    vi.mocked(fetch).mockResolvedValue(jsonResponse([sectionType]));
    const result = await httpEventsGateway.listSectionTypes();
    expect(result).toHaveLength(1);
    expect(result[0]?.label).toBe("Talk");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/section-types");
    expect(call?.[1]?.method ?? "GET").toBe("GET");
  });

  it("createSectionType POSTs to /axonia/section-types and returns the new type", async () => {
    const sectionType = {
      id: 2,
      label: "Workshop",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    };
    vi.mocked(fetch).mockResolvedValue(jsonResponse(sectionType, 201));
    const result = await httpEventsGateway.createSectionType({
      label: "Workshop",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    });
    expect(result.label).toBe("Workshop");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/section-types");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("updateSectionType PUTs to /axonia/section-types/{id} and returns the updated type", async () => {
    const sectionType = {
      id: 1,
      label: "Lightning Talk",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    };
    vi.mocked(fetch).mockResolvedValue(jsonResponse(sectionType));
    const result = await httpEventsGateway.updateSectionType(1, {
      label: "Lightning Talk",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    });
    expect(result.label).toBe("Lightning Talk");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/section-types/1");
    expect(call?.[1]?.method).toBe("PUT");
  });

  it("deleteSectionType DELETEs /axonia/section-types/{id} with no suffix", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await httpEventsGateway.deleteSectionType(1);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/section-types/1");
    expect(call?.[1]?.method).toBe("DELETE");
    expect(urlOf(call?.[0])).not.toContain("reassignTo");
  });

  it("deleteSectionType DELETEs with reassignTo query param when provided", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await httpEventsGateway.deleteSectionType(1, 2);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/section-types/1");
    expect(urlOf(call?.[0])).toContain("reassignTo=2");
    expect(call?.[1]?.method).toBe("DELETE");
  });
});
