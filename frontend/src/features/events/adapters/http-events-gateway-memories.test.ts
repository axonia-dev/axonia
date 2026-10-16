import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";
import { aScheduleEntry } from "@/test/session-fixture";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const lastUrl = () => urlOf(vi.mocked(fetch).mock.calls[0]?.[0]);

describe("httpEventsGateway: memories and ribbon", () => {
  it("getMemories asks for the first page by size alone", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ items: [], next: null }));

    const page = await httpEventsGateway.getMemories("x", null, 20);

    expect(page).toEqual({ items: [], next: null });
    expect(lastUrl()).toContain(
      "/axonia/communities/x/schedule/memories?size=20",
    );
    expect(lastUrl()).not.toContain("before");
  });

  it("getMemories resumes before the cursor and parses the traces", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        items: [
          {
            entry: aScheduleEntry({ status: "HELD" }),
            traces: { loves: 3, recorded: true, artifacts: 1 },
          },
        ],
        next: { at: "2026-08-01T18:00:00Z", id: 2 },
      }),
    );

    const page = await httpEventsGateway.getMemories(
      "x",
      { at: "2026-09-01T18:00:00Z", id: 4 },
      20,
    );

    expect(page.items[0]?.traces.loves).toBe(3);
    expect(lastUrl()).toContain("before=2026-09-01T18%3A00%3A00Z");
    expect(lastUrl()).toContain("beforeId=4");
    expect(vi.mocked(fetch).mock.calls[0]?.[1]?.headers).not.toHaveProperty(
      "Content-Type",
    );
  });

  it("getRibbon GETs the window and parses the ticks", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        firstSessionAt: "2026-03-01T18:00:00Z",
        ticks: [{ at: "2026-03-01T18:00:00Z", kind: "MEMORY" }],
      }),
    );

    const ribbon = await httpEventsGateway.getRibbon(
      "x",
      "2025-10-16T00:00:00Z",
      "2027-01-14T00:00:00Z",
    );

    expect(ribbon.ticks[0]?.kind).toBe("MEMORY");
    expect(lastUrl()).toContain("/axonia/communities/x/schedule/ribbon?");
    expect(lastUrl()).toContain("from=2025-10-16");
    expect(lastUrl()).toContain("to=2027-01-14");
  });
});
