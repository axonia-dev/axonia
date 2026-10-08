import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

const topic = {
  id: 5,
  communityId: 1,
  kind: "REQUEST",
  title: "ES intro",
  description: "please",
  tags: [],
  proposerUserId: "user-1",
  proposerDisplayName: "Alice",
  voteCount: 3,
  hasVoted: false,
  canEdit: false,
  canDelete: false,
  status: "PROPOSED",
  createdAt: "x",
  updatedAt: "x",
};

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("httpEventsGateway topics", () => {
  it("proposeTopic POSTs to the community topics endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(topic, 201));
    const created = await httpEventsGateway.proposeTopic("backend-guild", {
      kind: "REQUEST",
      title: "ES intro",
    });
    expect(created.id).toBe(5);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/topics",
    );
    expect(call?.[1]?.method).toBe("POST");
  });

  it("getTopicCounts fetches the community counts endpoint and parses the buckets", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ proposed: 3, scheduled: 1, done: 5 }),
    );
    const counts = await httpEventsGateway.getTopicCounts("backend-guild");
    expect(counts).toEqual({ proposed: 3, scheduled: 1, done: 5 });
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/topics/counts",
    );
  });

  it("voteTopic POSTs to the votes endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...topic, voteCount: 4 }),
    );
    const voted = await httpEventsGateway.voteTopic(5);
    expect(voted.voteCount).toBe(4);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/topics/5/votes");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("unvoteTopic issues a DELETE with no body", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await httpEventsGateway.unvoteTopic(5);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/topics/5/votes/me");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("editTopic PUTs to /topics/{id} with the new fields and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...topic, title: "Renamed" }),
    );
    const updated = await httpEventsGateway.editTopic(5, {
      kind: "OFFER",
      title: "Renamed",
      description: "now an offer",
    });
    expect(updated.id).toBe(5);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/topics/5");
    expect(call?.[1]?.method).toBe("PUT");
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      kind: "OFFER",
      title: "Renamed",
      description: "now an offer",
    });
  });

  it("deleteTopic DELETEs the /topics/{id} endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await httpEventsGateway.deleteTopic(5);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/topics/5");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("proposeTopic sends tags in the request body", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(topic, 201));
    await httpEventsGateway.proposeTopic("backend-guild", {
      kind: "REQUEST",
      title: "ES intro",
      tags: ["typescript", "ddd"],
    });
    const call = vi.mocked(fetch).mock.calls[0];
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      tags: ["typescript", "ddd"],
    });
  });

  it("editTopic sends tags in the request body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...topic, title: "Renamed" }),
    );
    await httpEventsGateway.editTopic(5, {
      kind: "OFFER",
      title: "Renamed",
      tags: ["refactoring"],
    });
    const call = vi.mocked(fetch).mock.calls[0];
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      tags: ["refactoring"],
    });
  });
});
