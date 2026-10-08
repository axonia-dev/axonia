import { afterEach, describe, expect, it, vi } from "vitest";

import {
  getAccessToken,
  notifyUnauthorized,
  setAccessToken,
  setUnauthorizedHandler,
} from "@/auth/token";

afterEach(() => {
  setAccessToken(undefined);
  setUnauthorizedHandler(undefined);
});

describe("access token seam", () => {
  it("returns the token that was set", () => {
    // given
    setAccessToken("token-123");

    // when
    const actual = getAccessToken();

    // then
    expect(actual).toBe("token-123");
  });

  it("returns undefined before any token is set", () => {
    // when
    const actual = getAccessToken();

    // then
    expect(actual).toBeUndefined();
  });
});

describe("unauthorized handler", () => {
  it("invokes the registered handler", () => {
    // given
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    // when
    notifyUnauthorized("a-token");

    // then
    expect(handler).toHaveBeenCalledOnce();
  });

  it("is a no-op when no handler is registered", () => {
    // when / then — must not throw
    expect(() => notifyUnauthorized("a-token")).not.toThrow();
  });

  it("hands the rejection code to the registered handler", () => {
    // given
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    // when
    notifyUnauthorized("a-token", "session_revoked");

    // then
    expect(handler).toHaveBeenCalledWith("a-token", "session_revoked");
  });

  it("hands over undefined when the rejection carried no code", () => {
    // given
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    // when
    notifyUnauthorized("a-token");

    // then
    expect(handler).toHaveBeenCalledWith("a-token", undefined);
  });
});
