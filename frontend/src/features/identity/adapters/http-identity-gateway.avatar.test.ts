import { afterEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

const PROFILE = {
  id: "alice",
  displayName: "Alice Anderson",
  avatarUrl: "https://cdn.example/own.png",
  memberStatus: "PRESENT",
};

describe("httpIdentityGateway avatar", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("puts the file as multipart and parses the refreshed profile", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(jsonResponse(PROFILE));

    const user = await httpIdentityGateway.setAvatar(
      new File(["bytes"], "me.png", { type: "image/png" }),
    );

    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toContain("/axonia/me/avatar");
    expect(init?.method).toBe("PUT");
    expect(init?.body).toBeInstanceOf(FormData);
    // The browser must set the multipart boundary itself; a manual Content-Type 415s.
    expect(new Headers(init?.headers).has("content-type")).toBe(false);
    expect(user.avatarUrl).toBe("https://cdn.example/own.png");
  });

  it("deletes the override and parses the refreshed profile", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(jsonResponse({ ...PROFILE, avatarUrl: null }));

    const user = await httpIdentityGateway.clearAvatar();

    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toContain("/axonia/me/avatar");
    expect(init?.method).toBe("DELETE");
    expect(user.avatarUrl).toBeNull();
  });
});
