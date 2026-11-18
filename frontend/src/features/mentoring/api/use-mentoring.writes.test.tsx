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
  useJournalWrite,
  useOpenWords,
  useWords,
} from "@/features/mentoring/api/use-mentoring";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: {
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
    openWords: vi.fn(),
    answer: vi.fn(),
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

const topic = { label: "Kafka", area: null };
const note = { body: "Met", meetingOn: null, topicIds: [] };
const pointer = { topicId: null, noteId: 2, url: "https://example.com" };

type Write = ReturnType<typeof useJournalWrite>;

describe("useJournalWrite", () => {
  it.each([
    {
      act: (w: Write) => w.addTopic.mutate(topic),
      method: () => gateway.addTopic,
      args: [11, topic],
    },
    {
      act: (w: Write) => w.updateTopic.mutate({ id: 3, topic }),
      method: () => gateway.updateTopic,
      args: [11, 3, topic],
    },
    {
      act: (w: Write) => w.removeTopic.mutate(3),
      method: () => gateway.removeTopic,
      args: [11, 3],
    },
    {
      act: (w: Write) => w.addNote.mutate(note),
      method: () => gateway.addNote,
      args: [11, note],
    },
    {
      act: (w: Write) => w.updateNote.mutate({ id: 4, note }),
      method: () => gateway.updateNote,
      args: [11, 4, note],
    },
    {
      act: (w: Write) => w.removeNote.mutate(4),
      method: () => gateway.removeNote,
      args: [11, 4],
    },
    {
      act: (w: Write) => w.addPointer.mutate(pointer),
      method: () => gateway.addPointer,
      args: [11, pointer],
    },
    {
      act: (w: Write) => w.removePointer.mutate(5),
      method: () => gateway.removePointer,
      args: [11, 5],
    },
  ])(
    "writes and refreshes the journal and My mentoring",
    async ({ act, method, args }) => {
      // given
      vi.mocked(method()).mockResolvedValue(undefined);
      const invalidate = vi.spyOn(client, "invalidateQueries");
      const { result } = renderHook(() => useJournalWrite(11), { wrapper });

      // when
      act(result.current);

      // then
      await waitFor(() => expect(vi.mocked(method())).toHaveBeenCalled());
      expect(vi.mocked(method()).mock.calls[0]).toEqual(args);
      await waitFor(() =>
        expect(invalidatedKeys(invalidate)).toEqual([
          mentoringKeys.journal(11),
          mentoringKeys.mine(),
        ]),
      );
    },
  );
});

type WordsActions = ReturnType<typeof useWords>;

describe("useWords", () => {
  it.each([
    {
      act: (w: WordsActions) => w.write.mutate("Thanks"),
      method: () => gateway.writeWords,
      args: [11, "Thanks"],
    },
    {
      act: (w: WordsActions) => w.takeBack.mutate(),
      method: () => gateway.takeBackWords,
      args: [11],
    },
  ])(
    "refreshes the journal, My mentoring and the received words",
    async ({ act, method, args }) => {
      // given
      vi.mocked(method()).mockResolvedValue(undefined);
      const invalidate = vi.spyOn(client, "invalidateQueries");
      const { result } = renderHook(() => useWords(11), { wrapper });

      // when
      act(result.current);

      // then
      await waitFor(() => expect(vi.mocked(method())).toHaveBeenCalled());
      expect(vi.mocked(method()).mock.calls[0]).toEqual(args);
      await waitFor(() =>
        expect(invalidatedKeys(invalidate)).toEqual([
          mentoringKeys.journal(11),
          mentoringKeys.mine(),
          mentoringKeys.words(),
        ]),
      );
    },
  );
});

describe("useOpenWords", () => {
  it("opens the envelope and refreshes the received words", async () => {
    // given
    gateway.openWords.mockResolvedValue(undefined);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useOpenWords(), { wrapper });

    // when
    result.current.mutate(11);

    // then
    await waitFor(() => expect(gateway.openWords).toHaveBeenCalledWith(11));
    await waitFor(() =>
      expect(invalidatedKeys(invalidate)).toEqual([mentoringKeys.words()]),
    );
  });
});

describe("reopening a declined proposal", () => {
  it("refreshes My mentoring and the guild's directory", async () => {
    // given
    gateway.answer.mockResolvedValue(null);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useAnswerProposal(), { wrapper });

    // when
    result.current.mutate({
      id: 7,
      communitySlug: "platform",
      answer: "reopen",
    });

    // then
    await waitFor(() =>
      expect(gateway.answer).toHaveBeenCalledWith(7, "reopen"),
    );
    await waitFor(() =>
      expect(invalidatedKeys(invalidate)).toEqual([
        mentoringKeys.mine(),
        mentoringKeys.community("platform"),
      ]),
    );
  });
});

describe("a refused write", () => {
  it("refreshes the journal that has ended under it", async () => {
    // given
    gateway.addNote.mockRejectedValue(
      new ApiError(409, "ended", "MENTORING_PAIRING_ENDED"),
    );
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useJournalWrite(11), { wrapper });

    // when
    result.current.addNote.mutate(note);

    // then
    await waitFor(() => expect(result.current.addNote.isError).toBe(true));
    expect(invalidatedKeys(invalidate)).toEqual([
      mentoringKeys.journal(11),
      mentoringKeys.mine(),
    ]);
  });

  it("refreshes a proposal that closed under it", async () => {
    // given
    gateway.answer.mockRejectedValue(
      new ApiError(409, "closed", "MENTORING_PROPOSAL_CLOSED"),
    );
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useAnswerProposal(), { wrapper });

    // when
    result.current.mutate({
      id: 7,
      communitySlug: "platform",
      answer: "accept",
    });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(invalidatedKeys(invalidate)).toEqual([
      mentoringKeys.mine(),
      mentoringKeys.community("platform"),
    ]);
  });
});
