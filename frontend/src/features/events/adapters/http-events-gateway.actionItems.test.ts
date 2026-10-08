import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

const actionItem = {
  id: 1,
  communityId: 7,
  title: "Close the Jira ticket",
  description: "",
  tags: [],
  raisedByUserId: "u1",
  raisedByDisplayName: "Ada",
  assigneeUserId: null,
  assigneeDisplayName: null,
  dueDate: null,
  originSessionId: null,
  checkpoint: {
    sessionId: 12,
    scheduledAt: "2026-09-24T09:00:00Z",
    pinned: false,
  },
  carryCount: 0,
  outcome: "OPEN",
  canClose: true,
  canReopen: false,
  canEdit: true,
  canDelete: true,
  createdAt: "2026-09-19T08:00:00Z",
  updatedAt: "2026-09-19T08:00:00Z",
};

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("httpEventsGateway action items", () => {
  it("listActionItems GETs the community action-items endpoint with the outcome filter", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        content: [actionItem],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      }),
    );

    const page = await httpEventsGateway.listActionItems("backend-guild", {
      outcome: "OPEN",
    });

    expect(page.content[0]?.id).toBe(1);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/action-items",
    );
    expect(urlOf(call?.[0])).toContain("outcome=OPEN");
  });

  /** `checkpoint` is one of the two order keys the server recognises, and the only one that
   *  answers "soonest first". An unrecognised key is refused outright, not ignored. */
  it("listActionItems passes the requested order through to the server", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        content: [actionItem],
        page: { size: 50, number: 0, totalElements: 1, totalPages: 1 },
      }),
    );

    await httpEventsGateway.listActionItems("backend-guild", {
      outcome: "OPEN",
      sort: "checkpoint",
      page: 0,
      size: 50,
    });

    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("sort=checkpoint");
    expect(urlOf(call?.[0])).toContain("size=50");
  });

  it("raiseActionItem POSTs to the community action-items endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(actionItem, 201));

    const created = await httpEventsGateway.raiseActionItem("backend-guild", {
      title: "Close the Jira ticket",
      assignee: "bob",
      dueDate: "2026-08-01",
      checkpointSessionId: 12,
    });

    expect(created.id).toBe(1);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/action-items",
    );
    expect(call?.[1]?.method).toBe("POST");
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      title: "Close the Jira ticket",
      assignee: "bob",
      dueDate: "2026-08-01",
      checkpointSessionId: 12,
    });
  });

  it("editActionItem PUTs to /action-items/{id} with the new fields", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...actionItem, title: "Renamed" }),
    );

    const updated = await httpEventsGateway.editActionItem(1, {
      title: "Renamed",
      checkpointSessionId: null,
    });

    expect(updated.title).toBe("Renamed");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/action-items/1");
    expect(call?.[1]?.method).toBe("PUT");
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      title: "Renamed",
      checkpointSessionId: null,
    });
  });

  it("deleteActionItem DELETEs /action-items/{id}", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    await httpEventsGateway.deleteActionItem(1);

    const [input, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(urlOf(input)).toContain("/axonia/action-items/1");
    expect(init?.method).toBe("DELETE");
  });

  it("closeActionItem POSTs to the close endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...actionItem, outcome: "DONE" }),
    );

    const result = await httpEventsGateway.closeActionItem(1);

    const [input, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(urlOf(input)).toContain("/axonia/action-items/1/close");
    expect(init?.method).toBe("POST");
    expect(result.outcome).toBe("DONE");
  });

  it("dropActionItem POSTs to the drop endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...actionItem, outcome: "DROPPED" }),
    );

    const result = await httpEventsGateway.dropActionItem(1);

    const [input, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(urlOf(input)).toContain("/axonia/action-items/1/drop");
    expect(init?.method).toBe("POST");
    expect(result.outcome).toBe("DROPPED");
  });

  it("reopenActionItem POSTs to the reopen endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...actionItem, outcome: "OPEN" }),
    );

    const result = await httpEventsGateway.reopenActionItem(1);

    const [input, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(urlOf(input)).toContain("/axonia/action-items/1/reopen");
    expect(init?.method).toBe("POST");
    expect(result.outcome).toBe("OPEN");
  });
});
