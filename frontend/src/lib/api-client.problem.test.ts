import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiFetch } from "@/lib/api-client";

afterEach(() => {
  vi.restoreAllMocks();
});

function refuse(body: object) {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 400,
      headers: { "content-type": "application/problem+json" },
    }),
  );
}

describe("a refused request value", () => {
  it("carries the field the problem names", async () => {
    // given
    refuse({
      code: "field_invalid",
      field: "values.fromAddress",
      detail: "values.fromAddress: must be one bare mail address.",
    });

    // when
    const actual = await apiFetch("/axonia/mail-server/test").catch(
      (e: unknown) => e,
    );

    // then
    expect(actual).toBeInstanceOf(ApiError);
    expect(actual).toMatchObject({
      status: 400,
      code: "field_invalid",
      field: "values.fromAddress",
      detail: "values.fromAddress: must be one bare mail address.",
    });
  });

  it("carries no field when the problem names none, even if its detail reads like one", async () => {
    // given
    refuse({
      code: "field_invalid",
      detail: "values.fromAddress: must be one bare mail address.",
    });

    // when
    const actual = await apiFetch("/axonia/mail-server/test").catch(
      (e: unknown) => e,
    );

    // then
    expect(actual).toBeInstanceOf(ApiError);
    expect((actual as ApiError).field).toBeUndefined();
  });

  it("ignores a field that is not text", async () => {
    // given
    refuse({ code: "field_invalid", field: 42 });

    // when
    const actual = await apiFetch("/axonia/mail-server/test").catch(
      (e: unknown) => e,
    );

    // then
    expect((actual as ApiError).field).toBeUndefined();
  });

  it("makes the same error as the constructor for a problem with no field", () => {
    // when
    const actual = ApiError.fromProblem(409, "Reload it.", {
      code: "stale_revision",
      detail: "Reload it.",
    });

    // then
    expect(actual).toEqual(
      new ApiError(409, "Reload it.", "stale_revision", "Reload it."),
    );
    expect(actual.field).toBeUndefined();
  });
});
