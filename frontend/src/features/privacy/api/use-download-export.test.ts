import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  classifyDownloadError,
  downloadExportErrorMessage,
  useDownloadExport,
} from "@/features/privacy/api/use-download-export";
import * as apiClient from "@/lib/api-client";
import { ApiError } from "@/lib/api-client";

vi.mock("@/lib/api-client", async (importActual) => {
  const actual = await importActual<typeof import("@/lib/api-client")>();
  return { ...actual, apiFetchBlob: vi.fn() };
});

const mockApiFetchBlob = vi.mocked(apiClient.apiFetchBlob);

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  mockApiFetchBlob.mockReset();
});

describe("classifyDownloadError", () => {
  it("reads the rate-limit code, not just the 429 status", () => {
    const error = new ApiError(429, "x", "EXPORT_RATE_LIMITED");
    expect(classifyDownloadError(error)).toBe("rate_limited");
  });

  it("reads the own sign-in code, not just the 403 status", () => {
    const error = new ApiError(403, "x", "REQUIRES_OWN_SESSION");
    expect(classifyDownloadError(error)).toBe("refused");
  });

  it("classifies an uncoded 403 as forbidden, never as an own sign-in refusal", () => {
    // A missing permission or a Hub calling the whole-org export both throw a
    // bare ForbiddenException with no `code` at all - status alone must not
    // be told apart from REQUIRES_OWN_SESSION, or this tells the wrong story.
    const error = new ApiError(403, "x");
    expect(classifyDownloadError(error)).toBe("forbidden");
  });

  it("classifies a non-ApiError failure as unknown", () => {
    expect(classifyDownloadError(new Error("network down"))).toBe("unknown");
  });

  it("classifies an unrelated ApiError status/code as unknown", () => {
    expect(classifyDownloadError(new ApiError(500, "x"))).toBe("unknown");
  });
});

describe("downloadExportErrorMessage", () => {
  it("names the own sign-in only for the refused kind", () => {
    expect(downloadExportErrorMessage("refused")).toMatch(/own sign-in/i);
  });

  it("never mentions the own sign-in for a plain forbidden refusal", () => {
    const message = downloadExportErrorMessage("forbidden");
    expect(message).not.toMatch(/own sign-in/i);
    expect(message).toMatch(/access/i);
  });

  it("names the rate limit for the rate-limited kind", () => {
    expect(downloadExportErrorMessage("rate_limited")).toMatch(/many/i);
  });
});

describe("useDownloadExport", () => {
  it("starts idle", () => {
    const { result } = renderHook(() => useDownloadExport("/x"));
    expect(result.current.isPending).toBe(false);
    expect(result.current.isError).toBe(false);
  });

  it("is pending while the archive streams, then settles on success", async () => {
    const { promise, resolve } = deferred<apiClient.DownloadedFile>();
    mockApiFetchBlob.mockReturnValue(promise);
    const { result } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate());
    expect(result.current.isPending).toBe(true);

    resolve({ blob: new Blob(["zip"]), filename: "acme.zip" });
    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(false);
  });

  it("never leaves isPending true after a rate-limited refusal", async () => {
    mockApiFetchBlob.mockRejectedValue(
      new ApiError(429, "x", "EXPORT_RATE_LIMITED"),
    );
    const { result } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate());
    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(true);
  });

  it("never leaves isPending true after an own sign-in refusal", async () => {
    mockApiFetchBlob.mockRejectedValue(
      new ApiError(403, "x", "REQUIRES_OWN_SESSION"),
    );
    const { result } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate());
    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(true);
  });

  it("never leaves isPending true after an uncoded forbidden refusal", async () => {
    mockApiFetchBlob.mockRejectedValue(new ApiError(403, "x"));
    const { result } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate());
    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(true);
  });

  it("never leaves isPending true after a network failure", async () => {
    mockApiFetchBlob.mockRejectedValue(new Error("network down"));
    const { result } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate());
    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(true);
  });

  it("passes the raw error to the caller's onError", async () => {
    const thrown = new ApiError(429, "x", "EXPORT_RATE_LIMITED");
    mockApiFetchBlob.mockRejectedValue(thrown);
    const onError = vi.fn();
    const { result } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate(undefined, { onError }));
    await waitFor(() => expect(onError).toHaveBeenCalledWith(thrown));
  });

  it("does not update state once its caller has unmounted", async () => {
    const { promise, resolve } = deferred<apiClient.DownloadedFile>();
    mockApiFetchBlob.mockReturnValue(promise);
    const { result, unmount } = renderHook(() => useDownloadExport("/x"));

    act(() => result.current.mutate());
    unmount();

    // Resolving after unmount must not throw, and the last frozen snapshot must
    // stay exactly what it was at unmount - still pending, never flipped back.
    await act(async () => {
      resolve({ blob: new Blob(["zip"]), filename: "acme.zip" });
      await promise;
    });
    expect(result.current.isPending).toBe(true);
  });
});
