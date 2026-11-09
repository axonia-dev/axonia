import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys, topicKeys } from "@/features/events/api/query-keys";
import {
  useAddPresenter,
  useRemovePresenter,
} from "@/features/events/api/use-slot-presenters";
import {
  usePresentSlot,
  useWithdrawSlot,
} from "@/features/events/api/use-slot-sign-up";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    presentSlot: vi.fn(),
    withdrawSlot: vi.fn(),
    addPresenter: vi.fn(),
    removePresenter: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);
const session = { id: 7 } as never;

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function seed() {
  const topics = topicKeys.list("guild", { page: 0, size: 50 });
  const detail = sessionKeys.detail(7);
  client.setQueryData(topics, { content: [] });
  client.setQueryData(detail, session);
  return { topics, detail };
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  gateway.presentSlot.mockResolvedValue(session);
  gateway.withdrawSlot.mockResolvedValue(session);
  gateway.addPresenter.mockResolvedValue({
    session,
    assigned: true,
    reason: null,
  });
  gateway.removePresenter.mockResolvedValue(session);
});

type Mutate = () => Promise<unknown>;

const MUTATIONS: ReadonlyArray<[string, () => Mutate]> = [
  [
    "signing up",
    () => {
      const present = usePresentSlot("guild", 7, 3, 9);
      return () => present.mutateAsync();
    },
  ],
  [
    "withdrawing",
    () => {
      const withdraw = useWithdrawSlot("guild", 7, 3, 9);
      return () => withdraw.mutateAsync();
    },
  ],
  [
    "naming a presenter",
    () => {
      const add = useAddPresenter("guild", 7);
      return () =>
        add.mutateAsync({ sectionId: 3, slotId: 9, presenter: "u-bob" });
    },
  ],
  [
    "removing a presenter",
    () => {
      const remove = useRemovePresenter("guild", 7);
      return () =>
        remove.mutateAsync({ sectionId: 3, slotId: 9, userId: "u-bob" });
    },
  ],
];

describe("slot presenter changes", () => {
  it.each(MUTATIONS)(
    "refresh the community's topics after %s, since a scheduled topic's speakers are its slot's presenters",
    async (_, useMutate) => {
      // given
      const { topics, detail } = seed();
      const { result } = renderHook(useMutate, { wrapper });

      // when
      await act(async () => {
        await result.current();
      });

      // then
      expect(client.getQueryState(topics)?.isInvalidated).toBe(true);
      expect(client.getQueryState(detail)?.isInvalidated).toBe(true);
    },
  );
});
