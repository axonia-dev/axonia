import { useState } from "react";

import { useMe } from "@/features/identity/api/use-me";
import { ApiError } from "@/lib/api-client";
import {
  useSaveMailServer,
  useTestMailServer,
} from "@/selfhosted/features/mail-server/api/use-mail-server";
import {
  type MailServerView,
  MailValueRefused,
  type TestOutcome,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  REFUSED_CALLS,
  refusedValueLine,
} from "@/selfhosted/features/mail-server/domain/mail-server-copy";
import {
  draftOf,
  type DraftProblems,
  draftProblems,
  edited,
  keyOf,
  type MailServerDraft,
  recipientProblem,
  refusalProblems,
  testKey,
  valuesOf,
} from "@/selfhosted/features/mail-server/domain/mail-server-form";
import {
  type Failures,
  type Sent,
  useFailures,
} from "@/selfhosted/features/mail-server/ui/use-failures";

type Tested = Readonly<{ key: string; outcome: TestOutcome }>;

const NO_PROBLEMS: DraftProblems = {};

/** The words for a refused call, or `undefined` for the generic failure line. */
export function refusedCallMessage(error: unknown): string | undefined {
  return error instanceof ApiError
    ? REFUSED_CALLS[error.code ?? ""]
    : undefined;
}

function outcomeFor(tested: Tested | null, key: string): TestOutcome | null {
  return tested !== null && tested.key === key ? tested.outcome : null;
}

/** Prefilled with the caller's own address until something is typed. */
function useRecipient() {
  const me = useMe();
  const [typed, setTyped] = useState<string | null>(null);
  return [typed ?? me.data?.email ?? "", setTyped] as const;
}

type Refusal = Readonly<{ refused: MailValueRefused; sent: Sent }>;

/** What the form holds now: the key of its values and its recipient as typed. */
type Holding = Readonly<{ key: string; recipient: string }>;

/** Whether a refusal still says something about the form: a refused recipient while the form
 *  holds the recipient sent, a refused value while it holds the values sent. An edit made while
 *  the call was in flight leaves a refusal about what the form no longer holds, which says
 *  nothing, and an edit of anything else leaves it standing. */
function stillSpeaks(
  refused: MailValueRefused,
  sent: Sent,
  holding: Holding,
): boolean {
  return refused.field === "recipient"
    ? sent.recipient === holding.recipient.trim()
    : keyOf(sent.values) === holding.key;
}

function currentRefusal(
  failures: Failures,
  holding: Holding,
): Refusal | undefined {
  const refused = [failures.test, failures.save].find(
    (failure) => failure instanceof MailValueRefused,
  );
  const { sent } = failures;
  if (refused === undefined || sent === null) return undefined;
  return stillSpeaks(refused, sent, holding) ? { refused, sent } : undefined;
}

/** A refused value is marked on its own field, so it says nothing more beneath the form. */
function shownFailure(failure: Error | null): Error | null {
  return failure instanceof MailValueRefused ? null : failure;
}

/** Once a save or a clear is refused as stale, it says so until the form closes. */
function saveNotice(failures: Failures) {
  return failures.stale
    ? { error: true, message: REFUSED_CALLS.stale_revision }
    : {
        error: shownFailure(failures.save),
        message: refusedCallMessage(failures.save),
      };
}

function recipientError(
  checked: boolean,
  recipient: string,
  refused: MailValueRefused | undefined,
): string | undefined {
  const typed = checked ? recipientProblem(recipient) : undefined;
  return (
    typed ??
    (refused?.field === "recipient"
      ? refusedValueLine({
          field: "recipient",
          setByDeployment: false,
          sent: true,
        })
      : undefined)
  );
}

function isRefusedPassword(error: Error): error is MailValueRefused {
  return error instanceof MailValueRefused && error.field === "password";
}

function hasNone(problems: DraftProblems): boolean {
  return Object.keys(problems).length === 0;
}

/**
 * The open form. A test result counts only while the form holds exactly the values it tested
 * (`testKey`), so any edit disables Save and undoing it enables Save again, as the server's token
 * is bound to the same values. The revision is the one the form opened at, never a later read.
 * A refusal lasts until the next edit, test or save, and speaks only while the form holds what
 * was refused (`stillSpeaks`); a stale one lasts until the form closes. `problemsRaised` counts
 * the tests held back by a problem and the calls refused for a value, each a cue to move focus.
 */
export function useMailServerForm(view: MailServerView, onSaved: () => void) {
  const [draft, setDraft] = useState(() => draftOf(view));
  const [baseRevision] = useState(view.revision);
  const [recipient, setRecipient] = useRecipient();
  const [checked, setChecked] = useState(false);
  const [tested, setTested] = useState<Tested | null>(null);
  const [problemsRaised, setProblemsRaised] = useState(0);
  const failures = useFailures();
  const test = useTestMailServer();
  const save = useSaveMailServer();
  const key = testKey(draft);
  const outcome = outcomeFor(tested, key);
  const blocking = draftProblems(draft, view);
  const refusal = currentRefusal(failures, { key, recipient });
  const refused = refusal?.refused;
  const raiseProblem = () => setProblemsRaised((count) => count + 1);

  /** A password the installation refused is asked for again, even over the stored one's dots,
   *  while that refusal still speaks. */
  function askAgainIfRefused(error: Error, sent: Sent) {
    if (!isRefusedPassword(error) || view.password.lockedBy !== null) return;
    setDraft((current) =>
      stillSpeaks(error, sent, { key: testKey(current), recipient })
        ? edited(current, { replaceRequested: true }, view)
        : current,
    );
  }

  function sendTest() {
    setChecked(true);
    failures.forget();
    if (!hasNone(blocking) || recipientProblem(recipient) !== undefined) {
      raiseProblem();
      return;
    }
    test.mutate(
      { values: valuesOf(draft), recipient: recipient.trim() },
      {
        onSuccess: (result) => setTested({ key, outcome: result }),
        onError: (error, input) => {
          const sent = { values: input.values, recipient: input.recipient };
          askAgainIfRefused(error, sent);
          failures.failTest(error, sent);
          if (error instanceof MailValueRefused) raiseProblem();
        },
      },
    );
  }

  function saveTested() {
    if (outcome === null || !outcome.accepted) return;
    failures.forget();
    save.mutate(
      {
        values: valuesOf(draft),
        proof: { testToken: outcome.testToken, baseRevision },
      },
      {
        onSuccess: onSaved,
        onError: (error, input) => {
          if (error instanceof ApiError && error.code === "untested") {
            setTested(null);
          }
          const sent = { values: input.values, recipient: null };
          askAgainIfRefused(error, sent);
          failures.failSave(error, sent);
          if (error instanceof MailValueRefused) raiseProblem();
        },
      },
    );
  }

  return {
    draft,
    baseRevision,
    change: (patch: Partial<MailServerDraft>) => {
      setDraft((current) => edited(current, patch, view));
      failures.forget();
    },
    problems: {
      ...(checked ? blocking : NO_PROBLEMS),
      ...refusalProblems(refused, refusal?.sent.values, view),
    },
    recipient,
    recipientError: recipientError(checked, recipient, refused),
    setRecipient: (typed: string) => {
      setRecipient(typed);
      failures.forget();
    },
    outcome,
    sendTest,
    isTesting: test.isPending,
    testError: shownFailure(failures.test),
    testMessage: refusedCallMessage(failures.test),
    canSave:
      outcome?.accepted === true &&
      hasNone(blocking) &&
      !failures.stale &&
      !save.isPending,
    save: saveTested,
    isSaving: save.isPending,
    saveNotice: saveNotice(failures),
    markStale: failures.markStale,
    problemsRaised,
  };
}

export type MailServerFormState = ReturnType<typeof useMailServerForm>;
