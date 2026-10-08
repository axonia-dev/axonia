import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpReactionGateway } from "@/features/recognition/adapters/http-reaction-gateway";
import { stubFetchJson, urlOf } from "@/test/fetch";

beforeEach(() => {
  vi.restoreAllMocks();
});

/** The plain headers object api-client builds for a call - no Headers instance involved. */
function headersOf(init: RequestInit | undefined): Record<string, string> {
  return (init?.headers ?? {}) as Record<string, string>;
}

function contentTypesOf(init: RequestInit | undefined): string[] {
  return Object.entries(headersOf(init))
    .filter(([key]) => key.toLowerCase() === "content-type")
    .map(([, value]) => value);
}

describe("httpReactionGateway", () => {
  it("sends the emoji arm and no definitionId", async () => {
    // given
    const fetchSpy = stubFetchJson({
      kind: "EMOJI",
      emoji: "\u{1F9E0}",
      definitionId: null,
      name: null,
      imageUrl: null,
      count: 1,
      viewerHasReacted: true,
    });

    // when
    await httpReactionGateway.react(
      { contributionType: "SLOT_PRESENTED", contributionId: 1 },
      { kind: "EMOJI", emoji: "\u{1F9E0}" },
    );

    // then
    const init = fetchSpy.mock.calls[0]?.[1];
    const body = JSON.parse(init?.body as string) as Record<string, unknown>;
    expect(body).toMatchObject({ kind: "EMOJI", emoji: "\u{1F9E0}" });
    expect(body.definitionId).toBeUndefined();
  });

  /** A Content-Type set at a call site appends rather than replaces and 415s the request. */
  it("sends exactly one application/json content type on a JSON call", async () => {
    // given
    const fetchSpy = stubFetchJson({
      kind: "EMOJI",
      emoji: "\u{1F9E0}",
      definitionId: null,
      name: null,
      imageUrl: null,
      count: 1,
      viewerHasReacted: true,
    });

    // when
    await httpReactionGateway.react(
      { contributionType: "SLOT_PRESENTED", contributionId: 1 },
      { kind: "EMOJI", emoji: "\u{1F9E0}" },
    );

    // then
    const init = fetchSpy.mock.calls[0]?.[1];
    expect(contentTypesOf(init)).toEqual(["application/json"]);
  });

  it("posts a proposal as multipart, letting the browser set the boundary", async () => {
    // given
    const fetchSpy = stubFetchJson({
      id: 1,
      name: "yak",
      imageUrl: "https://cdn/y.png",
      pending: true,
      proposedByUserId: "u",
    });

    // when
    await httpReactionGateway.propose(
      "yak",
      new File([new Uint8Array([1])], "y.png", { type: "image/png" }),
    );

    // then
    const init = fetchSpy.mock.calls[0]?.[1];
    expect(init?.body).toBeInstanceOf(FormData);
    expect(contentTypesOf(init)).toEqual([]);
  });

  /** The member-scoped read, which must not be the permission-gated `/all` one. */
  it("asks the caller-scoped path for its own proposals", async () => {
    // given
    const fetchSpy = stubFetchJson([
      {
        id: 1,
        name: "yak",
        imageUrl: "https://cdn/y.png",
        pending: true,
        proposedByUserId: "u",
      },
    ]);

    // when
    const mine = await httpReactionGateway.listMyProposals();

    // then
    expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
      "/reaction-definitions/mine",
    );
    expect(mine).toHaveLength(1);
  });

  it("puts the ref on the query string when withdrawing", async () => {
    // given
    const fetchSpy = stubFetchJson(undefined, 204);

    // when
    await httpReactionGateway.unreact(
      { contributionType: "SLOT_PRESENTED", contributionId: 1 },
      { kind: "CUSTOM", definitionId: 9 },
    );

    // then
    const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
    expect(url).toContain("kind=CUSTOM");
    expect(url).toContain("definitionId=9");
    expect(url).not.toContain("emoji=");
  });
});
