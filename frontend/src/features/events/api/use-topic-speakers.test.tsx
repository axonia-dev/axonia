import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { topicKeys } from "@/features/events/api/query-keys";
import {
  useAddSelfAsSpeaker,
  useAddSpeaker,
  useRemoveSpeaker,
  useWithdrawAsSpeaker,
} from "@/features/events/api/use-topic-speakers";
import type { Topic } from "@/features/events/domain/topic";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    addSelfAsSpeaker: vi.fn(),
    addSpeaker: vi.fn(),
    withdrawAsSpeaker: vi.fn(),
    removeSpeaker: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

const topic = { id: 5 } as Topic;

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function seedBacklog() {
  const key = topicKeys.list("guild", { page: 0, size: 20 });
  client.setQueryData(key, { content: [] });
  return key;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("useAddSelfAsSpeaker", () => {
  it("adds the viewer and refreshes the community's topics", async () => {
    // given
    gateway.addSelfAsSpeaker.mockResolvedValue(topic);
    const key = seedBacklog();
    const { result } = renderHook(() => useAddSelfAsSpeaker("guild", 5), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync();
    });

    // then
    expect(gateway.addSelfAsSpeaker).toHaveBeenCalledWith(5);
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  });
});

describe("speaker changes and expertise", () => {
  it("refresh the community's expertise, since an offer counts for its speakers", async () => {
    // given
    gateway.addSelfAsSpeaker.mockResolvedValue(topic);
    const key = expertiseKeys.map("guild");
    client.setQueryData(key, {});
    const { result } = renderHook(() => useAddSelfAsSpeaker("guild", 5), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync();
    });

    // then
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  });
});

describe("useAddSpeaker", () => {
  it("names the speaker and refreshes the community's topics", async () => {
    // given
    gateway.addSpeaker.mockResolvedValue({
      topic,
      assigned: true,
      reason: null,
    });
    const key = seedBacklog();
    const { result } = renderHook(() => useAddSpeaker("guild", 5), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync("u-bob");
    });

    // then
    expect(gateway.addSpeaker).toHaveBeenCalledWith(5, "u-bob");
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  });

  it("refreshes the topics after a refusal, so a topic scheduled meanwhile leaves the backlog", async () => {
    // given
    gateway.addSpeaker.mockRejectedValue(
      new ApiError(409, "x", "NOT_PROPOSED"),
    );
    const key = seedBacklog();
    const { result } = renderHook(() => useAddSpeaker("guild", 5), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync("u-bob").catch(() => undefined);
    });

    // then
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  });
});

describe("useWithdrawAsSpeaker", () => {
  it("withdraws the viewer and refreshes the community's topics", async () => {
    // given
    gateway.withdrawAsSpeaker.mockResolvedValue(topic);
    const key = seedBacklog();
    const { result } = renderHook(() => useWithdrawAsSpeaker("guild", 5), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync();
    });

    // then
    expect(gateway.withdrawAsSpeaker).toHaveBeenCalledWith(5);
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  });
});

describe("useRemoveSpeaker", () => {
  it("removes that speaker and refreshes the community's topics", async () => {
    // given
    gateway.removeSpeaker.mockResolvedValue(topic);
    const key = seedBacklog();
    const { result } = renderHook(() => useRemoveSpeaker("guild", 5), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync("u-bob");
    });

    // then
    expect(gateway.removeSpeaker).toHaveBeenCalledWith(5, "u-bob");
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  });
});
