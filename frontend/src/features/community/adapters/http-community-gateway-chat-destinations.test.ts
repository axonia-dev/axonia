import { afterEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { stubFetchJson, urlOf } from "@/test/fetch";

function stubFetchNoContent() {
  const fetchSpy = vi.spyOn(globalThis, "fetch");
  fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));
  return fetchSpy;
}

const DESTINATION = {
  id: 7,
  name: "#guild",
  platform: "SLACK",
  maskedUrl: "hooks.slack.com/...abcd",
  broken: false,
};

describe("httpCommunityGateway chat destinations", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("parses a list of destinations", async () => {
    stubFetchJson([{ ...DESTINATION, lastPostedAt: "2026-08-06T09:00:00Z" }]);

    await expect(
      httpCommunityGateway.listChatDestinations("guild"),
    ).resolves.toEqual([
      expect.objectContaining({ id: 7, name: "#guild", platform: "SLACK" }),
    ]);
  });

  // apiFetch always sets its own Content-Type internally, so the header the real
  // fetch() call receives is never literally undefined - the regression this guards
  // against is a gateway call site ALSO passing its own Content-Type, which a plain
  // object merge does not overwrite when the caller's key differs in case, and a
  // real Headers object then appends into "application/json, application/json",
  // which the backend answers with a 415. Asserting the normalized header stays a
  // single, undoubled value is what actually catches that regression.
  it("creates a destination with no Content-Type of its own", async () => {
    const fetchSpy = stubFetchJson(DESTINATION);

    await httpCommunityGateway.createChatDestination("guild", {
      name: "#guild",
      platform: "SLACK",
      webhookUrl: "https://hooks.slack.com/services/x",
    });

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/communities/guild/chat-destinations");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("content-type")).toBe(
      "application/json",
    );
  });

  it("patches only the name when no replacement url is given", async () => {
    const fetchSpy = stubFetchJson({ ...DESTINATION, name: "#renamed" });

    await httpCommunityGateway.updateChatDestination("guild", 7, {
      name: "#renamed",
      platform: "SLACK",
      webhookUrl: null,
    });

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/guild/chat-destinations/7",
    );
    expect(init?.method).toBe("PATCH");
    expect(init?.body).toBe(
      JSON.stringify({
        name: "#renamed",
        platform: "SLACK",
        webhookUrl: null,
      }),
    );
  });

  it("removes a destination", async () => {
    const fetchSpy = stubFetchNoContent();

    await httpCommunityGateway.removeChatDestination("guild", 7);

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/guild/chat-destinations/7",
    );
    expect(init?.method).toBe("DELETE");
  });

  it("posts a test message with no body", async () => {
    const fetchSpy = stubFetchNoContent();

    await httpCommunityGateway.testChatDestination("guild", 7);

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/guild/chat-destinations/7/test",
    );
    expect(init?.method).toBe("POST");
  });
});
