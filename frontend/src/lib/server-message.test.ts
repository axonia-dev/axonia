import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/api-client";
import { serverMessage } from "@/lib/server-message";

describe("serverMessage", () => {
  it("returns the message of an ApiError that carries a code", () => {
    const error = new ApiError(422, "Name already taken", "NAME_TAKEN");
    expect(serverMessage(error)).toBe("Name already taken");
  });

  it("returns null for an ApiError with no code", () => {
    const error = new ApiError(500, "Something went wrong");
    expect(serverMessage(error)).toBeNull();
  });

  it("returns null for a plain Error", () => {
    expect(serverMessage(new Error("boom"))).toBeNull();
  });

  it("returns null for undefined", () => {
    expect(serverMessage(undefined)).toBeNull();
  });
});
