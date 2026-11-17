import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import {
  beforeEach,
  describe,
  expect,
  it,
  type MockInstance,
  vi,
} from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journalOf, topicOf } from "@/features/mentoring/test-data";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: { placeTopic: vi.fn() },
}));

const gateway = vi.mocked(httpMentoringGateway);

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function invalidatedKeys(spy: MockInstance<QueryClient["invalidateQueries"]>) {
  return spy.mock.calls.map(([filters]) => filters?.queryKey);
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("placing a topic", () => {
  const board = journalOf({
    topics: [
      topicOf({ id: 1, state: "TODO", position: 0 }),
      topicOf({ id: 2, state: "IN_PROGRESS", position: 0 }),
    ],
  });
  const toDone = { id: 1, placement: { state: "DONE" as const, position: 0 } };

  function cached() {
    return client
      .getQueryData<Journal>(mentoringKeys.journal(11))
      ?.topics.map(({ id, state }) => [id, state]);
  }

  it("moves the sticker in the journal before the server answers", async () => {
    // given
    client.setQueryData(mentoringKeys.journal(11), board);
    gateway.placeTopic.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useJournalWrite(11), { wrapper });

    // when
    result.current.placeTopic.mutate(toDone);

    // then
    await waitFor(() => expect(gateway.placeTopic).toHaveBeenCalled());
    expect(gateway.placeTopic.mock.calls[0]).toEqual([11, 1, toDone.placement]);
    expect(cached()).toEqual([
      [2, "IN_PROGRESS"],
      [1, "DONE"],
    ]);
  });

  it("puts the sticker back when the move is refused", async () => {
    // given
    client.setQueryData(mentoringKeys.journal(11), board);
    gateway.placeTopic.mockRejectedValue(
      new ApiError(409, "ended", "MENTORING_PAIRING_ENDED"),
    );
    vi.spyOn(client, "invalidateQueries").mockResolvedValue();
    const { result } = renderHook(() => useJournalWrite(11), { wrapper });

    // when
    result.current.placeTopic.mutate(toDone);

    // then
    await waitFor(() => expect(result.current.placeTopic.isError).toBe(true));
    expect(cached()).toEqual([
      [1, "TODO"],
      [2, "IN_PROGRESS"],
    ]);
  });

  it("refreshes the journal and My mentoring once settled", async () => {
    // given
    client.setQueryData(mentoringKeys.journal(11), board);
    gateway.placeTopic.mockResolvedValue(undefined);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useJournalWrite(11), { wrapper });

    // when
    result.current.placeTopic.mutate(toDone);

    // then
    await waitFor(() =>
      expect(invalidatedKeys(invalidate)).toEqual([
        mentoringKeys.journal(11),
        mentoringKeys.mine(),
      ]),
    );
  });
});
