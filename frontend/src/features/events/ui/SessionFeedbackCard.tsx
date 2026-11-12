import { Check } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { notifyError } from "@/components/ui/toast";
import { useGiveSessionFeedback } from "@/features/events/api/use-session-feedback";
import type {
  FeedbackAnswer,
  SessionFeedback,
} from "@/features/events/domain/session-feedback";
import { closesOn } from "@/features/events/ui/feedback-closes-on";
import {
  moteFrom,
  settleIn,
  squeeze,
} from "@/features/events/ui/feedback-motion";
import type { FeedbackQuestion } from "@/features/events/ui/feedback-scrub";
import {
  FEEDBACK_SCALE,
  feedbackWord,
} from "@/features/events/ui/feedback-scrub";
import { FeedbackRow } from "@/features/events/ui/FeedbackRow";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

interface Props {
  slug: string;
  sessionId: number;
  feedback: SessionFeedback;
}

const SAVE_DELAY_MS = 600;
const BARS = Array.from({ length: FEEDBACK_SCALE }, (_, index) => index + 1);

const CARD_CLASS =
  "bg-surface relative overflow-hidden rounded-lg border border-[color-mix(in_srgb,var(--primary)_30%,var(--border))] px-[18px] pt-[18px] pb-4 shadow-[0_6px_24px_color-mix(in_srgb,var(--primary)_12%,transparent)] before:pointer-events-none before:absolute before:inset-0 before:bg-[radial-gradient(120%_70%_at_100%_0%,color-mix(in_srgb,var(--primary)_10%,transparent),transparent_60%)] before:content-['']";

function MiniRow({
  question,
  label,
  value,
}: Readonly<{ question: FeedbackQuestion; label: string; value: number }>) {
  return (
    <span className="text-muted-foreground flex items-center gap-1.5 text-[11px]">
      {label}
      <span
        role="img"
        aria-label={`${label} ${feedbackWord(question, value)}`}
        className="flex h-4 items-end gap-0.5"
      >
        {BARS.map((bar) => (
          <span
            key={bar}
            className={cn(
              "w-1 rounded-[2px]",
              bar <= value ? "bg-data-1" : "bg-data-area",
            )}
            style={{ height: `${4 + bar * 1.2}px` }}
          />
        ))}
      </span>
    </span>
  );
}

function SentLine({
  mine,
  closesAt,
  open,
  onChange,
}: Readonly<{
  mine: FeedbackAnswer;
  closesAt: string | null;
  open: boolean;
  onChange: () => void;
}>) {
  const until = closesAt ? closesOn(closesAt) : null;
  let note = "Anonymous";
  if (until)
    note = open
      ? `Anonymous · open until ${until}`
      : `Anonymous · closed ${until}`;
  return (
    <div className="relative flex flex-wrap items-center gap-x-3.5 gap-y-2.5">
      <span className="text-data-1 grid size-[34px] flex-none place-items-center rounded-full bg-[color-mix(in_srgb,var(--primary)_16%,var(--surface))]">
        <Check size={18} strokeWidth={2.4} aria-hidden="true" />
      </span>
      <div className="min-w-[150px]">
        <strong className="font-display text-foreground block text-[15px]">
          Feedback saved
        </strong>
        <small className="text-muted-foreground text-xs">{note}</small>
      </div>
      <div className="ml-auto flex flex-wrap items-center gap-x-3.5 gap-y-2.5">
        <MiniRow question="enjoyed" label="Enjoyed" value={mine.enjoyed} />
        <MiniRow question="learned" label="Learned" value={mine.learned} />
        {open && (
          <Button variant="outline" size="sm" onPress={onChange}>
            Change
          </Button>
        )}
      </div>
    </div>
  );
}

type SaveState = "incomplete" | "saving" | "saved" | "failed";

function SaveStatus({ state }: Readonly<{ state: SaveState }>) {
  return (
    <p
      role="status"
      className="text-muted-foreground flex min-h-5 items-center gap-1.5 text-xs"
    >
      {state === "saved" && (
        <span ref={settleIn} className="text-data-1 inline-flex">
          <Check size={14} strokeWidth={2.6} aria-hidden="true" />
        </span>
      )}
      {saveWords(state)}
    </p>
  );
}

function saveWords(state: SaveState): string {
  switch (state) {
    case "incomplete":
      return "Anonymous · saves once both are set";
    case "saving":
      return "Saving…";
    case "saved":
      return "Saved · anonymous";
    case "failed":
      return "Not saved · a tap tries again";
  }
}

/**
 * The ask on a held session's page: two rows of bars that save on their own once both hold an
 * answer, so a tap is the whole gesture. A later visit finds a one line receipt that stays
 * changeable while the window is open.
 */
export function SessionFeedbackCard({
  slug,
  sessionId,
  feedback,
}: Readonly<Props>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const give = useGiveSessionFeedback(slug, sessionId);
  const open = feedback.window === "OPEN";
  const [asking, setAsking] = useState(open && feedback.mine === null);
  const [enjoyed, setEnjoyed] = useState<number | null>(null);
  const [learned, setLearned] = useState<number | null>(null);
  const [state, setState] = useState<SaveState>("incomplete");
  const cardRef = useRef<HTMLDivElement | null>(null);
  const statusRef = useRef<HTMLDivElement | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const pending = useRef<FeedbackAnswer | null>(null);
  const queued = useRef<FeedbackAnswer | null>(null);
  const inFlight = useRef(false);
  const celebrated = useRef(false);
  const saveRef = useRef<(answer: FeedbackAnswer) => void>(() => undefined);

  const until = feedback.closesAt ? closesOn(feedback.closesAt) : null;

  function save(answer: FeedbackAnswer) {
    if (inFlight.current) {
      queued.current = answer;
      return;
    }
    inFlight.current = true;
    give.mutate(answer, {
      onSuccess: () => {
        if (queued.current || pending.current) return;
        setState("saved");
        if (!celebrated.current && !reduced) {
          moteFrom(statusRef.current, cardRef.current);
          squeeze(cardRef.current);
        }
        celebrated.current = true;
      },
      onError: () => {
        if (queued.current || pending.current) return;
        setState("failed");
        notifyError("Feedback not saved");
      },
      onSettled: () => {
        inFlight.current = false;
        const next = queued.current;
        queued.current = null;
        if (next) save(next);
      },
    });
  }
  useLayoutEffect(() => {
    saveRef.current = save;
  });

  useEffect(
    () => () => {
      clearTimeout(timer.current);
      const last = pending.current ?? queued.current;
      if (!last) return;
      inFlight.current = false;
      saveRef.current(last);
    },
    [],
  );

  function answer(next: { enjoyed: number | null; learned: number | null }) {
    setEnjoyed(next.enjoyed);
    setLearned(next.learned);
    if (next.enjoyed === null || next.learned === null) return;
    const pair = { enjoyed: next.enjoyed, learned: next.learned };
    setState("saving");
    clearTimeout(timer.current);
    pending.current = pair;
    timer.current = setTimeout(() => {
      pending.current = null;
      saveRef.current(pair);
    }, SAVE_DELAY_MS);
  }

  function change() {
    setEnjoyed(feedback.mine?.enjoyed ?? null);
    setLearned(feedback.mine?.learned ?? null);
    setState("saved");
    celebrated.current = true;
    setAsking(true);
  }

  return (
    <div ref={cardRef} data-motes-host className={CARD_CLASS}>
      {asking || !feedback.mine ? (
        <div className="relative">
          <div className="mb-3.5 flex items-baseline justify-between gap-2.5">
            <h4 className="font-display text-foreground text-[17px] font-semibold">
              How was it?
            </h4>
            {until && (
              <span className="text-muted-foreground text-xs whitespace-nowrap">
                Open until {until}
              </span>
            )}
          </div>
          <div className="mb-4">
            <FeedbackRow
              question="enjoyed"
              label="Enjoyed it"
              ends={["Not really", "Loved it"]}
              value={enjoyed}
              onChange={(value) => answer({ enjoyed: value, learned })}
            />
          </div>
          <div className="mb-3">
            <FeedbackRow
              question="learned"
              label="Learned something"
              ends={["Nothing new", "A lot"]}
              value={learned}
              onChange={(value) => answer({ enjoyed, learned: value })}
            />
          </div>
          <div ref={statusRef} className="inline-flex">
            <SaveStatus state={state} />
          </div>
        </div>
      ) : (
        <SentLine
          mine={feedback.mine}
          closesAt={feedback.closesAt}
          open={open}
          onChange={change}
        />
      )}
    </div>
  );
}
