import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    dismissSuggestion: vi.fn(),
    restoreSuggestion: vi.fn(),
  },
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

import { notify } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { useTagDismissal } from "@/features/expertise/api/use-tag-dismissal";

const gateway = vi.mocked(httpExpertiseGateway);
const notifyMock = vi.mocked(notify);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useTagDismissal", () => {
  beforeEach(() => vi.clearAllMocks());

  it("dismisses the pair and raises an undo toast", async () => {
    // given
    gateway.dismissSuggestion.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagDismissal("eng"), { wrapper });

    // when
    result.current.dismissPair("spring", "spring-boot");

    // then
    await waitFor(() =>
      expect(gateway.dismissSuggestion.mock.calls).toHaveLength(1),
    );
    expect(gateway.dismissSuggestion.mock.calls[0]).toEqual([
      "eng",
      "spring",
      "spring-boot",
    ]);
    await waitFor(() => expect(notifyMock).toHaveBeenCalledTimes(1));
    expect(notifyMock.mock.calls[0]?.[0]).toContain("spring");
  });

  it("undo restores the dismissed pair", async () => {
    // given
    gateway.dismissSuggestion.mockResolvedValue(undefined);
    gateway.restoreSuggestion.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagDismissal("eng"), { wrapper });

    // when
    result.current.dismissPair("spring", "spring-boot");
    await waitFor(() => expect(notifyMock).toHaveBeenCalled());
    const action = notifyMock.mock.calls[0]?.[1];
    action?.onPress();

    // then
    await waitFor(() =>
      expect(gateway.restoreSuggestion.mock.calls).toHaveLength(1),
    );
    expect(gateway.restoreSuggestion.mock.calls[0]).toEqual([
      "eng",
      "spring",
      "spring-boot",
    ]);
  });

  it("reports a failure toast when dismiss rejects", async () => {
    // given
    gateway.dismissSuggestion.mockRejectedValue(new Error("nope"));
    const { result } = renderHook(() => useTagDismissal("eng"), { wrapper });

    // when
    result.current.dismissPair("spring", "spring-boot");

    // then
    const { notifyError } = await import("@/components/ui/toast");
    await waitFor(() => expect(notifyError).toHaveBeenCalled());
  });

  it("reports a failure toast when undo restore rejects", async () => {
    // given
    gateway.dismissSuggestion.mockResolvedValue(undefined);
    gateway.restoreSuggestion.mockRejectedValue(new Error("nope"));
    const { result } = renderHook(() => useTagDismissal("eng"), { wrapper });

    // when
    result.current.dismissPair("spring", "spring-boot");
    await waitFor(() => expect(notifyMock).toHaveBeenCalled());
    const action = notifyMock.mock.calls[0]?.[1];
    action?.onPress();

    // then
    const { notifyError } = await import("@/components/ui/toast");
    await waitFor(() => expect(notifyError).toHaveBeenCalled());
  });
});
