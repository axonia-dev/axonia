import { describe, expect, it, vi } from "vitest";

import {
  failedToLoad,
  retryOfFailed,
  stillLoading,
} from "@/lib/failed-to-load";

function read(
  state: Readonly<{ failed: boolean; fetching?: boolean }>,
): Parameters<typeof retryOfFailed>[0][number] {
  return {
    data: state.failed ? undefined : "answered",
    errorUpdateCount: state.failed ? 1 : 0,
    isFetching: state.fetching ?? false,
    refetch: vi.fn(() => Promise.resolve()),
  } as never;
}

describe("failedToLoad", () => {
  it("holds through a retry, while the read has nothing to show", () => {
    expect(failedToLoad({ data: undefined, errorUpdateCount: 1 })).toBe(true);
  });

  it("is false once the read has an answer, even if a later refresh failed", () => {
    expect(failedToLoad({ data: "cached", errorUpdateCount: 1 })).toBe(false);
  });
});

describe("stillLoading", () => {
  it("is true before the first answer or failure", () => {
    expect(stillLoading({ data: undefined, errorUpdateCount: 0 })).toBe(true);
  });

  it("is false once the read failed, so a retry is offered instead", () => {
    expect(stillLoading({ data: undefined, errorUpdateCount: 1 })).toBe(false);
  });

  it("is false once the read has an answer", () => {
    expect(stillLoading({ data: "answered", errorUpdateCount: 0 })).toBe(false);
  });
});

describe("retryOfFailed", () => {
  it("asks again only for the reads that failed", async () => {
    const failed = read({ failed: true });
    const answered = read({ failed: false });

    await retryOfFailed([failed, answered]).refetch();

    expect(failed.refetch).toHaveBeenCalledOnce();
    expect(answered.refetch).not.toHaveBeenCalled();
  });

  it("is fetching while a failed read is being asked again", () => {
    const retrying = read({ failed: true, fetching: true });

    expect(retryOfFailed([retrying, read({ failed: false })]).isFetching).toBe(
      true,
    );
  });

  it("is not fetching for a read that answered and is merely refreshing", () => {
    const refreshing = read({ failed: false, fetching: true });

    expect(retryOfFailed([read({ failed: true }), refreshing]).isFetching).toBe(
      false,
    );
  });
});
