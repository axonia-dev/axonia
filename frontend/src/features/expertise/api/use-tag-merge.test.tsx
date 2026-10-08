import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: { mergeBatch: vi.fn(), split: vi.fn() },
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

import { notify } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { useTagMerge } from "@/features/expertise/api/use-tag-merge";

const gateway = vi.mocked(httpExpertiseGateway);
const notifyMock = vi.mocked(notify);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useTagMerge", () => {
  beforeEach(() => vi.clearAllMocks());

  it("merges the variants into the survivor and raises an undo toast", async () => {
    // given
    gateway.mergeBatch.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagMerge("eng"), { wrapper });

    // when
    result.current.mergeInto(["yep", "ok"], "yes");

    // then
    await waitFor(() => expect(gateway.mergeBatch.mock.calls).toHaveLength(1));
    expect(gateway.mergeBatch.mock.calls[0]).toEqual([
      "eng",
      ["yep", "ok"],
      "yes",
    ]);
    await waitFor(() => expect(notifyMock).toHaveBeenCalledTimes(1));
    expect(notifyMock.mock.calls[0]?.[0]).toContain("yes");
  });

  it("undo splits every merged variant back out", async () => {
    // given
    gateway.mergeBatch.mockResolvedValue(undefined);
    gateway.split.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagMerge("eng"), { wrapper });

    // when
    result.current.mergeInto(["yep", "ok"], "yes");
    await waitFor(() => expect(notifyMock).toHaveBeenCalled());
    const action = notifyMock.mock.calls[0]?.[1];
    action?.onPress();

    // then
    await waitFor(() => expect(gateway.split.mock.calls).toHaveLength(2));
    expect(gateway.split.mock.calls).toContainEqual(["eng", "yep"]);
    expect(gateway.split.mock.calls).toContainEqual(["eng", "ok"]);
  });

  it("reports a failure toast when the merge rejects", async () => {
    // given
    gateway.mergeBatch.mockRejectedValue(new Error("nope"));
    const { result } = renderHook(() => useTagMerge("eng"), { wrapper });

    // when
    result.current.mergeInto(["ok"], "yes");

    // then
    const { notifyError } = await import("@/components/ui/toast");
    await waitFor(() => expect(notifyError).toHaveBeenCalled());
  });
});
