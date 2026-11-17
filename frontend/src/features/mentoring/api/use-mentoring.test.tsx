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
import {
  useAnswerProposal,
  useEndPairing,
  useJournal,
  useMentoringPage,
  useMyMentoring,
  useMyWords,
  usePropose,
  useSetAvailability,
} from "@/features/mentoring/api/use-mentoring";
import {
  availability,
  journal,
  mine,
  page,
  pairing,
  proposal,
  words,
} from "@/features/mentoring/test-data";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: {
    page: vi.fn(),
    setAvailability: vi.fn(),
    propose: vi.fn(),
    mine: vi.fn(),
    answer: vi.fn(),
    journal: vi.fn(),
    end: vi.fn(),
    addTopic: vi.fn(),
    updateTopic: vi.fn(),
    removeTopic: vi.fn(),
    addNote: vi.fn(),
    updateNote: vi.fn(),
    removeNote: vi.fn(),
    addPointer: vi.fn(),
    removePointer: vi.fn(),
    writeWords: vi.fn(),
    takeBackWords: vi.fn(),
    myWords: vi.fn(),
  },
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

function usePageHook() {
  return useMentoringPage("platform");
}

function useJournalHook() {
  return useJournal(11);
}

describe("reads", () => {
  it.each([
    { hook: usePageHook, method: gateway.page, data: page, args: ["platform"] },
    { hook: useMyMentoring, method: gateway.mine, data: mine, args: [] },
    {
      hook: useJournalHook,
      method: gateway.journal,
      data: journal,
      args: [11],
    },
    { hook: useMyWords, method: gateway.myWords, data: [words], args: [] },
  ])("asks the gateway", async ({ hook, method, data, args }) => {
    // given
    (method as ReturnType<typeof vi.fn>).mockResolvedValue(data);

    // when
    const { result } = renderHook(
      hook as () => { data: unknown; isSuccess: boolean },
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(data);
    expect((method as ReturnType<typeof vi.fn>).mock.calls[0]).toEqual(args);
  });
});

describe("read retries", () => {
  it.each([
    ["a non-member", new ApiError(403, "forbidden")],
    ["switched off", new ApiError(409, "off", "MENTORING_OFF")],
    ["a delegated session", new ApiError(401, "own", "REQUIRES_OWN_SESSION")],
  ])("asks once when refused as %s", async (_name, error) => {
    // given
    gateway.page.mockRejectedValue(error);

    // when
    const { result } = renderHook(usePageHook, { wrapper });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(gateway.page).toHaveBeenCalledTimes(1);
  });

  it("asks a second time after a failure that may pass", async () => {
    // given
    gateway.page
      .mockRejectedValueOnce(new ApiError(503, "unavailable"))
      .mockResolvedValue(page);

    // when
    const { result } = renderHook(usePageHook, { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true), {
      timeout: 3000,
    });
    expect(gateway.page).toHaveBeenCalledTimes(2);
  });
});

describe("useSetAvailability", () => {
  it("saves and refreshes the page and My mentoring", async () => {
    // given
    gateway.setAvailability.mockResolvedValue(availability);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useSetAvailability("platform"), {
      wrapper,
    });

    // when
    result.current.mutate(availability);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.setAvailability.mock.calls[0]).toEqual([
      "platform",
      availability,
    ]);
    expect(invalidatedKeys(invalidate)).toEqual([
      mentoringKeys.page("platform"),
      mentoringKeys.mine(),
    ]);
  });
});

describe("usePropose", () => {
  it("proposes and refreshes the page and My mentoring", async () => {
    // given
    gateway.propose.mockResolvedValue(proposal);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => usePropose("platform"), { wrapper });
    const input = {
      toUserId: "u-1",
      asRole: "MENTEE" as const,
      topics: ["Kafka"],
      message: "Hi",
    };

    // when
    result.current.mutate(input);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.propose.mock.calls[0]).toEqual(["platform", input]);
    expect(invalidatedKeys(invalidate)).toEqual([
      mentoringKeys.page("platform"),
      mentoringKeys.mine(),
    ]);
  });
});

describe("useAnswerProposal", () => {
  it.each(["accept", "decline", "withdraw"] as const)(
    "%s refreshes My mentoring and the proposal's community",
    async (answer) => {
      // given
      gateway.answer.mockResolvedValue(answer === "accept" ? pairing : null);
      const invalidate = vi.spyOn(client, "invalidateQueries");
      const { result } = renderHook(() => useAnswerProposal(), { wrapper });

      // when
      result.current.mutate({ id: 7, communitySlug: "platform", answer });

      // then
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(gateway.answer.mock.calls[0]).toEqual([7, answer]);
      expect(invalidatedKeys(invalidate)).toEqual([
        mentoringKeys.mine(),
        mentoringKeys.community("platform"),
      ]);
    },
  );
});

describe("useAnswerProposal accepting", () => {
  it("hands the new pairing over before the lists are refreshed", async () => {
    // given
    gateway.answer.mockResolvedValue(pairing);
    const order: string[] = [];
    vi.spyOn(client, "invalidateQueries").mockImplementation(() => {
      order.push("refresh");
      return Promise.resolve();
    });
    const { result } = renderHook(
      () =>
        useAnswerProposal((accepted) => order.push(`accepted ${accepted.id}`)),
      { wrapper },
    );

    // when
    result.current.mutate({
      id: 7,
      communitySlug: "platform",
      answer: "accept",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(order).toEqual(["accepted 11", "refresh", "refresh"]);
  });

  it("hands nothing over for a decline", async () => {
    // given
    gateway.answer.mockResolvedValue(null);
    const onAccepted = vi.fn();
    const { result } = renderHook(() => useAnswerProposal(onAccepted), {
      wrapper,
    });

    // when
    result.current.mutate({
      id: 7,
      communitySlug: "platform",
      answer: "decline",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(onAccepted).not.toHaveBeenCalled();
  });
});

describe("useEndPairing", () => {
  it("ends and refreshes the journal, the lists, the guild and the words", async () => {
    // given
    gateway.end.mockResolvedValue(undefined);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useEndPairing(11, "platform"), {
      wrapper,
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.end.mock.calls[0]).toEqual([11]);
    expect(invalidatedKeys(invalidate)).toEqual([
      mentoringKeys.journal(11),
      mentoringKeys.mine(),
      mentoringKeys.community("platform"),
      mentoringKeys.words(),
    ]);
  });
});
