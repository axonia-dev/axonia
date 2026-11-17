import {
  type QueryKey,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { useIsDelegated } from "@/auth/delegated-session";
import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import {
  type Availability,
  type Journal,
  type MentoringPage,
  type PairingSummary,
  placeTopic,
} from "@/features/mentoring/domain/mentoring";
import type {
  NoteInput,
  PointerInput,
  ProposalAnswer,
  ProposalInput,
  TopicInput,
  TopicPlacement,
} from "@/features/mentoring/domain/mentoring-gateway";
import { isKnownRefusal } from "@/features/mentoring/mentoring-errors";

const gateway = httpMentoringGateway;

/** A known refusal answers the same on a second ask, so only a failure that may pass is retried. */
function retryUnlessRefused(failures: number, error: unknown): boolean {
  return failures < 1 && !isKnownRefusal(error);
}

/** A delegated session is refused every mentoring route, so none of these reads ever asks. */
export function useMentoringPage(slug: string) {
  const delegated = useIsDelegated();
  return useQuery({
    queryKey: mentoringKeys.page(slug),
    queryFn: () => gateway.page(slug),
    enabled: !delegated,
    retry: retryUnlessRefused,
  });
}

export function useMyMentoring() {
  const delegated = useIsDelegated();
  return useQuery({
    queryKey: mentoringKeys.mine(),
    queryFn: () => gateway.mine(),
    enabled: !delegated,
    retry: retryUnlessRefused,
  });
}

export function useJournal(pairingId: number) {
  const delegated = useIsDelegated();
  return useQuery({
    queryKey: mentoringKeys.journal(pairingId),
    queryFn: () => gateway.journal(pairingId),
    enabled: !delegated && Number.isInteger(pairingId),
    retry: retryUnlessRefused,
  });
}

export function useMyWords() {
  const delegated = useIsDelegated();
  return useQuery({
    queryKey: mentoringKeys.words(),
    queryFn: () => gateway.myWords(),
    enabled: !delegated,
    retry: retryUnlessRefused,
  });
}

/** Whether the shell offers My mentoring: the member sits in a mentoring guild, or has a proposal or pairing of any state. */
export function useHasMentoring(): boolean {
  const delegated = useIsDelegated();
  const { data } = useQuery({
    queryKey: mentoringKeys.presence(),
    queryFn: () => gateway.presence(),
    enabled: !delegated,
    retry: retryUnlessRefused,
  });
  return data?.inAMentoringGuild === true;
}

/** A mutation that refreshes the given keys once it settles: a refusal (an ended pairing, a closed proposal) means the shown state is stale too. */
function useRefreshing<TVariables, TResult>(
  mutationFn: (variables: TVariables) => Promise<TResult>,
  keys: (variables: TVariables) => QueryKey[],
  onSuccess?: (result: TResult) => void,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    ...(onSuccess === undefined ? {} : { onSuccess }),
    onSettled: (_result, _error, variables) =>
      Promise.all(
        keys(variables).map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      ),
  });
}

/** A switch or a step shows at once, before the server answers, and goes back if refused. */
export function useSetAvailability(slug: string) {
  const queryClient = useQueryClient();
  const pageKey = mentoringKeys.page(slug);
  return useMutation({
    mutationFn: (availability: Availability) =>
      gateway.setAvailability(slug, availability),
    onMutate: async (availability: Availability) => {
      await queryClient.cancelQueries({ queryKey: pageKey });
      const before = queryClient.getQueryData<MentoringPage>(pageKey);
      if (before !== undefined) {
        queryClient.setQueryData<MentoringPage>(pageKey, {
          ...before,
          mine: availability,
        });
      }
      return { before };
    },
    onError: (_error, _availability, context) => {
      if (context?.before !== undefined) {
        queryClient.setQueryData(pageKey, context.before);
      }
    },
    onSettled: () =>
      Promise.all(
        [pageKey, mentoringKeys.mine()].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      ),
  });
}

export function usePropose(slug: string) {
  return useRefreshing(
    (proposal: ProposalInput) => gateway.propose(slug, proposal),
    () => [mentoringKeys.page(slug), mentoringKeys.mine()],
  );
}

export interface ProposalAnswerInput {
  id: number;
  communitySlug: string;
  answer: ProposalAnswer;
}

/**
 * Whichever way it is answered, the guild's directory changes: a pairing, a declined, withdrawn or reopened proposal each move someone's proposal state.
 * `onAccepted` hears of the new pairing before the lists are refreshed, so it can be marked before it first shows.
 */
export function useAnswerProposal(
  onAccepted?: (pairing: PairingSummary) => void,
) {
  return useRefreshing(
    ({ id, answer }: ProposalAnswerInput) => gateway.answer(id, answer),
    ({ communitySlug }) => [
      mentoringKeys.mine(),
      mentoringKeys.community(communitySlug),
    ],
    (pairing) => {
      if (pairing !== null) onAccepted?.(pairing);
    },
  );
}

/** Ending frees a mentor's room and a pair's proposal state in the guild, and closes the journal. */
export function useEndPairing(pairingId: number, communitySlug: string) {
  return useRefreshing(
    () => gateway.end(pairingId),
    () => [
      mentoringKeys.journal(pairingId),
      mentoringKeys.mine(),
      mentoringKeys.community(communitySlug),
      mentoringKeys.words(),
    ],
  );
}

interface TopicMove {
  id: number;
  placement: TopicPlacement;
}

/** A sticker lands where it was dropped before the server answers, and goes back if refused. */
function usePlaceTopic(pairingId: number) {
  const queryClient = useQueryClient();
  const journalKey = mentoringKeys.journal(pairingId);
  return useMutation({
    mutationFn: ({ id, placement }: TopicMove) =>
      gateway.placeTopic(pairingId, id, placement),
    onMutate: async ({ id, placement }: TopicMove) => {
      await queryClient.cancelQueries({ queryKey: journalKey });
      const before = queryClient.getQueryData<Journal>(journalKey);
      if (before !== undefined) {
        queryClient.setQueryData<Journal>(journalKey, {
          ...before,
          topics: placeTopic(before.topics, id, placement),
        });
      }
      return { before };
    },
    onError: (_error, _move, context) => {
      if (context?.before !== undefined) {
        queryClient.setQueryData(journalKey, context.before);
      }
    },
    onSettled: () =>
      Promise.all(
        [journalKey, mentoringKeys.mine()].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      ),
  });
}

export function useJournalWrite(pairingId: number) {
  const keys = () => [mentoringKeys.journal(pairingId), mentoringKeys.mine()];
  return {
    placeTopic: usePlaceTopic(pairingId),
    addTopic: useRefreshing(
      (topic: TopicInput) => gateway.addTopic(pairingId, topic),
      keys,
    ),
    updateTopic: useRefreshing(
      ({ id, topic }: { id: number; topic: TopicInput }) =>
        gateway.updateTopic(pairingId, id, topic),
      keys,
    ),
    removeTopic: useRefreshing(
      (id: number) => gateway.removeTopic(pairingId, id),
      keys,
    ),
    addNote: useRefreshing(
      (note: NoteInput) => gateway.addNote(pairingId, note),
      keys,
    ),
    updateNote: useRefreshing(
      ({ id, note }: { id: number; note: NoteInput }) =>
        gateway.updateNote(pairingId, id, note),
      keys,
    ),
    removeNote: useRefreshing(
      (id: number) => gateway.removeNote(pairingId, id),
      keys,
    ),
    addPointer: useRefreshing(
      (pointer: PointerInput) => gateway.addPointer(pairingId, pointer),
      keys,
    ),
    removePointer: useRefreshing(
      (id: number) => gateway.removePointer(pairingId, id),
      keys,
    ),
  };
}

/** Words touch the journal's flags, the pairing lists and the mentor's received words. */
export function useWords(pairingId: number) {
  const keys = () => [
    mentoringKeys.journal(pairingId),
    mentoringKeys.mine(),
    mentoringKeys.words(),
  ];
  return {
    write: useRefreshing(
      (body: string) => gateway.writeWords(pairingId, body),
      keys,
    ),
    takeBack: useRefreshing(() => gateway.takeBackWords(pairingId), keys),
  };
}

/** The date sits on the journal, and My mentoring's cards show it too. */
export function useNextMeeting(pairingId: number) {
  const keys = () => [mentoringKeys.journal(pairingId), mentoringKeys.mine()];
  return {
    set: useRefreshing(
      (on: string) => gateway.setNextMeeting(pairingId, on),
      keys,
    ),
    clear: useRefreshing(() => gateway.clearNextMeeting(pairingId), keys),
  };
}

/** Opening an envelope is remembered by the server, so the received words read it back as open. */
export function useOpenWords() {
  return useRefreshing(
    (pairingId: number) => gateway.openWords(pairingId),
    () => [mentoringKeys.words()],
  );
}
