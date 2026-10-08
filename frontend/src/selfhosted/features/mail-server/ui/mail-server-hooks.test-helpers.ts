import { vi } from "vitest";

import {
  useClearMailServer,
  useMailConfigured,
  useMailServer,
  useMailServerChanges,
  useSaveMailServer,
  useTestMailServer,
} from "@/selfhosted/features/mail-server/api/use-mail-server";
import type {
  MailServerChange,
  MailServerView,
  TestOutcome,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import { CONFIGURED } from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";

type Settled<T> = Readonly<{
  onSuccess?: (result: T) => void;
  onError?: (error: unknown, input: unknown) => void;
}>;

function readOf<T>(data: T | undefined, failed: boolean) {
  return {
    data,
    errorUpdateCount: failed ? 1 : 0,
    isFetching: false,
    refetch: vi.fn(),
  } as never;
}

export function answerView(
  view: MailServerView | undefined,
  failed = false,
): void {
  vi.mocked(useMailServer).mockReturnValue(readOf(view, failed));
}

export function answerStatus(
  configured: boolean | undefined,
  failed = false,
): void {
  vi.mocked(useMailConfigured).mockReturnValue(readOf(configured, failed));
}

export function answerChanges(
  rows: ReadonlyArray<MailServerChange>,
  hasNextPage = false,
): void {
  vi.mocked(useMailServerChanges).mockReturnValue({
    ...(readOf({ pages: [{ content: rows }] }, false) as object),
    hasNextPage,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
}

/** The history's first page still on its way. */
export function answerChangesPending(): void {
  vi.mocked(useMailServerChanges).mockReturnValue({
    ...(readOf(undefined, false) as object),
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
}

/** Every test answers `outcome`; `null` answers nothing, or fails as `error` when one is given. */
export function answerTests(
  outcome: TestOutcome | null,
  error: unknown = null,
) {
  const mutate = vi.fn((_input: unknown, settled?: Settled<TestOutcome>) => {
    if (outcome !== null) settled?.onSuccess?.(outcome);
    else if (error !== null) settled?.onError?.(error, _input);
  });
  vi.mocked(useTestMailServer).mockReturnValue({
    mutate,
    isPending: false,
    error,
  } as never);
  return mutate;
}

/** Every test stays in flight until `refuse` answers it with `error`, as a slow server does. */
export function answerTestsLater() {
  let pending: (() => void) | undefined;
  const mutate = vi.fn((input: unknown, settled?: Settled<TestOutcome>) => {
    pending = () => settled?.onError?.(currentError, input);
  });
  let currentError: unknown = null;
  vi.mocked(useTestMailServer).mockReturnValue({
    mutate,
    isPending: false,
    error: null,
  } as never);
  return (error: unknown) => {
    currentError = error;
    pending?.();
  };
}

/** Every save succeeds with the configured view, or fails as `error`. */
export function answerSaves(error: unknown = null) {
  const mutate = vi.fn((_input: unknown, settled?: Settled<MailServerView>) => {
    if (error === null) settled?.onSuccess?.(CONFIGURED);
    else settled?.onError?.(error, _input);
  });
  vi.mocked(useSaveMailServer).mockReturnValue({
    mutate,
    isPending: false,
    error,
  } as never);
  return mutate;
}

/** Every clear succeeds, or fails as `error`. */
export function answerClears(error: unknown = null) {
  const mutate = vi.fn((_input: unknown, settled?: Settled<undefined>) => {
    if (error === null) settled?.onSuccess?.(undefined);
    else settled?.onError?.(error, _input);
  });
  vi.mocked(useClearMailServer).mockReturnValue({
    mutate,
    isPending: false,
    isError: error !== null,
    error,
  } as never);
  return mutate;
}
