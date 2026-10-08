import { afterEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";

const alice = {
  id: "11111111-1111-1111-1111-111111111111",
  displayName: "Alice Anderson",
  email: "alice@example.com",
  memberStatus: "PRESENT",
};

function mockJson(body: unknown) {
  return vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("httpIdentityGateway", () => {
  it("me fetches and parses the caller profile", async () => {
    // given
    const spy = mockJson(alice);

    // when
    const actual = await httpIdentityGateway.me();

    // then
    expect(actual).toEqual({
      ...alice,
      avatarIsOwn: false,
      timeZoneIsOwn: false,
    });
    const [requestInfo] = spy.mock.calls[0] ?? [];
    expect(typeof requestInfo === "string" ? requestInfo : "").toContain(
      "/axonia/me",
    );
  });

  it("resolve batches ids into one comma-separated query", async () => {
    // given
    const spy = mockJson([alice]);

    // when
    const actual = await httpIdentityGateway.resolve([alice.id, "other-sub"]);

    // then
    expect(actual).toEqual([
      { ...alice, avatarIsOwn: false, timeZoneIsOwn: false },
    ]);
    const [requestInfo] = spy.mock.calls[0] ?? [];
    expect(typeof requestInfo === "string" ? requestInfo : "").toContain(
      `/axonia/users?ids=${alice.id},other-sub`,
    );
  });

  it("resolve rejects a malformed payload", async () => {
    // given
    mockJson([{ nope: true }]);

    // when / then
    await expect(httpIdentityGateway.resolve(["x"])).rejects.toThrow();
  });
});
