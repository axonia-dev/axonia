import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fitImage } from "@/lib/fit-image";
import { useImageFit } from "@/lib/use-image-fit";

vi.mock("@/lib/fit-image", () => ({ fitImage: vi.fn() }));

const mockFitImage = vi.mocked(fitImage);
const LIMITS = { maxEdge: 256, maxBytes: 1024 };
const picked = new File(["picked"], "a.png", { type: "image/png" });
const fitted = new File(["fitted"], "a.png", { type: "image/png" });

function deferred() {
  let resolve: (file: File) => void = () => {};
  const promise = new Promise<File>((settle) => {
    resolve = settle;
  });
  return { promise, resolve };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useImageFit", () => {
  it("is fitting until the fit resolves, then hands back the fitted file", async () => {
    // given
    const pending = deferred();
    mockFitImage.mockReturnValueOnce(pending.promise);
    const { result } = renderHook(() => useImageFit(LIMITS));
    let actual: Promise<File | null> = Promise.resolve(null);

    // when
    act(() => {
      actual = result.current.fit(picked);
    });

    // then
    expect(result.current.fitting).toBe(true);
    expect(mockFitImage).toHaveBeenCalledWith(picked, LIMITS);
    await act(async () => {
      pending.resolve(fitted);
      expect(await actual).toBe(fitted);
    });
    expect(result.current.fitting).toBe(false);
  });

  it("drops a fit a later pick has overtaken", async () => {
    // given
    const first = deferred();
    mockFitImage.mockReturnValueOnce(first.promise);
    mockFitImage.mockResolvedValueOnce(fitted);
    const { result } = renderHook(() => useImageFit(LIMITS));
    let overtaken: Promise<File | null> = Promise.resolve(null);
    act(() => {
      overtaken = result.current.fit(picked);
    });
    await act(() => result.current.fit(picked));

    // when
    first.resolve(picked);
    const actual = await overtaken;

    // then
    expect(actual).toBeNull();
  });

  it("drops a fit that was discarded and stops fitting", async () => {
    // given
    const pending = deferred();
    mockFitImage.mockReturnValueOnce(pending.promise);
    const { result } = renderHook(() => useImageFit(LIMITS));
    let discarded: Promise<File | null> = Promise.resolve(null);
    act(() => {
      discarded = result.current.fit(picked);
    });

    // when
    act(() => result.current.discard());

    // then
    expect(result.current.fitting).toBe(false);
    pending.resolve(fitted);
    expect(await discarded).toBeNull();
  });
});
