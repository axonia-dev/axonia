import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import {
  useDismissSignal,
  useHideTag,
  useSetOptOut,
  useUnhideTag,
} from "@/features/expertise/api/use-expertise-prefs";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    setOptOut: vi.fn(),
    hideTag: vi.fn(),
    unhideTag: vi.fn(),
    dismiss: vi.fn(),
  },
}));

vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

import { notify, notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);
const notifyMock = vi.mocked(notify);
const notifyErrorMock = vi.mocked(notifyError);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function withCachedMap() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  client.setQueryData(expertiseKeys.map("platform"), { topics: [] });
  client.setQueryData(expertiseKeys.selfView(), { areas: [] });
  const cached = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const stale = (key: readonly unknown[]) =>
    client.getQueryState(key)?.isInvalidated;
  return { cached, stale };
}

type Wrapper = (props: { children: ReactNode }) => ReactNode;

const MUTATIONS: [string, (wrapper: Wrapper) => void][] = [
  [
    "an opt out",
    (w) => renderHook(useSetOptOut, { wrapper: w }).result.current.mutate(true),
  ],
  [
    "a hide",
    (w) => renderHook(useHideTag, { wrapper: w }).result.current.mutate("pg"),
  ],
  [
    "an unhide",
    (w) => renderHook(useUnhideTag, { wrapper: w }).result.current.mutate("pg"),
  ],
];

describe("a change to what the member shows", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    gateway.setOptOut.mockResolvedValue(undefined);
    gateway.hideTag.mockResolvedValue(undefined);
    gateway.unhideTag.mockResolvedValue(undefined);
  });

  it.each(MUTATIONS)(
    "marks each community's map stale after %s, since the map counts the member too",
    async (_, mutate) => {
      const { cached, stale } = withCachedMap();

      mutate(cached);

      await waitFor(() =>
        expect(stale(expertiseKeys.map("platform"))).toBe(true),
      );
      expect(stale(expertiseKeys.selfView())).toBe(true);
    },
  );
});

describe("useSetOptOut", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls gateway.setOptOut(true) when mutated with true", async () => {
    // given
    gateway.setOptOut.mockResolvedValue(undefined);
    const { result } = renderHook(() => useSetOptOut(), { wrapper });

    // when
    result.current.mutate(true);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.setOptOut.mock.calls[0]).toEqual([true]);
  });

  it("calls gateway.setOptOut(false) when mutated with false", async () => {
    // given
    gateway.setOptOut.mockResolvedValue(undefined);
    const { result } = renderHook(() => useSetOptOut(), { wrapper });

    // when
    result.current.mutate(false);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.setOptOut.mock.calls[0]).toEqual([false]);
  });
});

describe("useHideTag", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls gateway.hideTag with the tag name", async () => {
    // given
    gateway.hideTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useHideTag(), { wrapper });

    // when
    result.current.mutate("typescript");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.hideTag.mock.calls[0]).toEqual(["typescript"]);
  });
});

describe("useUnhideTag", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls gateway.unhideTag with the tag name", async () => {
    // given
    gateway.unhideTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useUnhideTag(), { wrapper });

    // when
    result.current.mutate("graphql");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.unhideTag.mock.calls[0]).toEqual(["graphql"]);
  });

  it("sends one unhide for a tag pressed twice before the first lands, and still sends another tag", async () => {
    // given
    gateway.unhideTag.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useUnhideTag(), { wrapper });

    // when
    result.current.mutate("graphql");
    result.current.mutate("graphql");
    result.current.mutate("kafka");

    // then
    await waitFor(() => expect(gateway.unhideTag).toHaveBeenCalledTimes(2));
    expect(gateway.unhideTag.mock.calls).toEqual([["graphql"], ["kafka"]]);
  });

  it("says an unhide failed", async () => {
    // given
    gateway.unhideTag.mockRejectedValue(new Error("offline"));
    const { result } = renderHook(() => useUnhideTag(), { wrapper });

    // when
    result.current.mutate("graphql");

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(notifyErrorMock).toHaveBeenCalledWith("Could not show that area.");
  });
});

describe("useDismissSignal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls gateway.dismiss with the full DismissInput", async () => {
    // given
    gateway.dismiss.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDismissSignal(), { wrapper });

    // when
    result.current.mutate({
      sourceType: "SLOT",
      sourceId: 42,
      tag: "typescript",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.dismiss.mock.calls[0]).toEqual([
      { sourceType: "SLOT", sourceId: 42, tag: "typescript" },
    ]);
  });

  it("calls gateway.dismiss with ARTIFACT source type", async () => {
    // given
    gateway.dismiss.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDismissSignal(), { wrapper });

    // when
    result.current.mutate({
      sourceType: "ARTIFACT",
      sourceId: 7,
      tag: "ddd",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.dismiss.mock.calls[0]).toEqual([
      { sourceType: "ARTIFACT", sourceId: 7, tag: "ddd" },
    ]);
  });

  it("raises a toast on success", async () => {
    // given
    gateway.dismiss.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDismissSignal(), { wrapper });

    // when
    result.current.mutate({ sourceType: "SLOT", sourceId: 1, tag: "kafka" });

    // then
    await waitFor(() =>
      expect(notifyMock).toHaveBeenCalledWith("Signal dismissed"),
    );
  });

  it("raises an error toast on failure", async () => {
    // given
    gateway.dismiss.mockRejectedValue(new Error("nope"));
    const { result } = renderHook(() => useDismissSignal(), { wrapper });

    // when
    result.current.mutate({ sourceType: "SLOT", sourceId: 1, tag: "kafka" });

    // then
    await waitFor(() => expect(notifyErrorMock).toHaveBeenCalled());
  });
});
