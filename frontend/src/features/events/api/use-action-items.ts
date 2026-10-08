import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { notifyError } from "@/components/ui/toast";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { actionItemKeys } from "@/features/events/api/query-keys";
import type {
  ActionItem,
  RaiseActionItemInput,
  UpdateActionItemInput,
} from "@/features/events/domain/action-item";
import { settledOrder } from "@/features/events/domain/action-item";
import type { ActionItemListParams } from "@/features/events/domain/events-gateway";

const gateway = httpEventsGateway;

export function useActionItems(slug: string, params: ActionItemListParams) {
  return useQuery({
    queryKey: actionItemKeys.list(slug, params),
    queryFn: () => gateway.listActionItems(slug, params),
  });
}

export function useRaiseActionItem(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RaiseActionItemInput) =>
      gateway.raiseActionItem(slug, input),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: actionItemKeys.community(slug),
      }),
  });
}

/** Correcting an open item: title, assignee, due date, and the session it is answered at. The
 *  backend refuses this on a settled item, so `canEdit` is false once it is - a closed item is
 *  corrected by reopen, edit, close. */
export function useEditActionItem(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: EditActionItemVariables) =>
      gateway.editActionItem(id, input),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: actionItemKeys.community(slug),
      }),
  });
}

export interface EditActionItemVariables {
  id: number;
  input: UpdateActionItemInput;
}

/** Removes an item raised by mistake. Distinct from dropping, which records that the community
 *  settled the work as one it will not do. */
export function useDeleteActionItem(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.deleteActionItem(id),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: actionItemKeys.community(slug),
      }),
  });
}

/** Settle order, not creation order: the server truncates each bucket to a page, so the column it
 *  truncates on has to be the column the tab displays by - otherwise an old item settled today
 *  falls off the page entirely instead of merely sorting late. */
const SETTLED_SORT = "updated_at,desc" as const;

const DONE_PARAMS: ActionItemListParams = {
  outcome: "DONE",
  sort: SETTLED_SORT,
  page: 0,
  size: 20,
};
const DROPPED_PARAMS: ActionItemListParams = {
  outcome: "DROPPED",
  sort: SETTLED_SORT,
  page: 0,
  size: 20,
};

export interface SettledActionItems {
  items: ActionItem[];
  /** Undefined until both pages have answered: a half-counted total reads as a smaller one. */
  total: number | undefined;
  isLoading: boolean;
  isError: boolean;
}

function totalOf(
  done: number | undefined,
  dropped: number | undefined,
): number | undefined {
  if (done === undefined || dropped === undefined) return undefined;
  return done + dropped;
}

/** Everything a community has settled, done and dropped together. `outcome` narrows to one
 *  bucket per request, so this is two reads merged rather than one. */
export function useSettledActionItems(slug: string): SettledActionItems {
  const done = useActionItems(slug, DONE_PARAMS);
  const dropped = useActionItems(slug, DROPPED_PARAMS);
  return {
    items: settledOrder([
      ...(done.data?.content ?? []),
      ...(dropped.data?.content ?? []),
    ]),
    total: totalOf(done.data?.totalElements, dropped.data?.totalElements),
    isLoading: done.isLoading || dropped.isLoading,
    isError: done.isError || dropped.isError,
  };
}

export type SettleOutcome = ActionItem["outcome"];

/** Open, done, dropped, open. Done and dropped are the two ways an item settles, so they share one
 *  control; the cycle's third press is the undo for either. */
export function nextOutcome(current: ActionItem["outcome"]): SettleOutcome {
  if (current === "OPEN") return "DONE";
  if (current === "DONE") return "DROPPED";
  return "OPEN";
}

function settleCall(id: number, outcome: SettleOutcome) {
  if (outcome === "DONE") return gateway.closeActionItem(id);
  if (outcome === "DROPPED") return gateway.dropActionItem(id);
  return gateway.reopenActionItem(id);
}

/** The row the server would answer with, written straight into the cache. The settle rights move
 *  with the cycle, and `canEdit` follows the outcome because the server refuses an edit to a
 *  settled item: left alone, a struck-through row would keep offering a correction it would then
 *  refuse. */
function settledActionItem(
  item: ActionItem,
  outcome: SettleOutcome,
): ActionItem {
  const permitted = item.canClose || item.canReopen;
  return {
    ...item,
    outcome,
    canClose: permitted && outcome !== "DROPPED",
    canReopen: permitted && outcome !== "OPEN",
    canEdit: item.canEdit && outcome === "OPEN",
  };
}

const SETTLE_FAILED = "Could not update that action point. Try again.";

/** Settles an action item from its own row. Own `useMutation` instance per item, so repeated
 *  presses on the SAME row serialise rather than race while another row's are never held up.
 *
 *  Optimistic: the control must move the instant it is pressed, not after a round trip. A failed
 *  settle rolls the cache back and says so here, at the mutation, rather than through a per-press
 *  `onError`: pressing again before the first press fails detaches that press's observer, so its
 *  callback never runs and the failure would pass in silence on exactly the cycle - press twice to
 *  correct a mistake - the control is built around. */
export function useSettleActionItem(
  slug: string,
  id: number,
  failureMessage: string = SETTLE_FAILED,
) {
  const queryClient = useQueryClient();
  const queryKey = actionItemKeys.community(slug);
  return useMutation({
    scope: { id: `action-item:${id}` },
    mutationFn: (outcome: SettleOutcome) => settleCall(id, outcome),
    onMutate: async (outcome) => {
      await queryClient.cancelQueries({ queryKey });
      const prev = queryClient.getQueriesData({ queryKey });
      queryClient.setQueriesData({ queryKey }, (old: unknown) =>
        patchActionItemInPage(old, id, (item) =>
          settledActionItem(item, outcome),
        ),
      );
      return { prev };
    },
    onError: (_error, _outcome, context) => {
      for (const [key, data] of context?.prev ?? []) {
        queryClient.setQueryData(key, data);
      }
      notifyError(failureMessage);
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey });
    },
  });
}

function patchActionItemInPage(
  old: unknown,
  id: number,
  patch: (item: ActionItem) => ActionItem,
): unknown {
  if (!old || typeof old !== "object" || !("content" in old)) return old;
  const page = old as { content: ActionItem[] };
  return {
    ...page,
    content: page.content.map((item) => (item.id === id ? patch(item) : item)),
  };
}
