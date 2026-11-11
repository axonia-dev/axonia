import { Check } from "lucide-react";
import { useRef, useState } from "react";

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

const FOLD_DELAY_MS = 220;
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
  animate,
}: Readonly<{
  mine: FeedbackAnswer;
  closesAt: string | null;
  open: boolean;
  onChange: () => void;
  animate: boolean;
}>) {
  const until = closesAt ? closesOn(closesAt) : null;
  let note = "Anonymous";
  if (until)
    note = open
      ? `Anonymous · open until ${until}`
      : `Anonymous · closed ${until}`;
  return (
    <div
      ref={animate ? settleIn : undefined}
      className="relative flex flex-wrap items-center gap-x-3.5 gap-y-2.5"
    >
      <span className="text-data-1 grid size-[34px] flex-none place-items-center rounded-full bg-[color-mix(in_srgb,var(--primary)_16%,var(--surface))]">
        <Check size={18} strokeWidth={2.4} aria-hidden="true" />
      </span>
      <div className="min-w-[150px]">
        <strong className="font-display text-foreground block text-[15px]">
          Feedback sent
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

/**
 * The ask on a held session's page: two rows of bars, one Send, then a one line receipt that
 * folds the rows away and stays changeable while the window is open.
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
  const [folded, setFolded] = useState(false);
  const [enjoyed, setEnjoyed] = useState<number | null>(null);
  const [learned, setLearned] = useState<number | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const sendRef = useRef<HTMLSpanElement | null>(null);

  const until = feedback.closesAt ? closesOn(feedback.closesAt) : null;

  function send() {
    if (enjoyed === null || learned === null) return;
    give.mutate(
      { enjoyed, learned },
      {
        onSuccess: () => {
          if (!reduced) {
            moteFrom(sendRef.current, cardRef.current);
            squeeze(cardRef.current);
          }
          globalThis.setTimeout(
            () => {
              setFolded(true);
              setAsking(false);
            },
            reduced ? 0 : FOLD_DELAY_MS,
          );
        },
        onError: () => {
          notifyError("Feedback not sent");
        },
      },
    );
  }

  function change() {
    setEnjoyed(feedback.mine?.enjoyed ?? null);
    setLearned(feedback.mine?.learned ?? null);
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
              onChange={setEnjoyed}
            />
          </div>
          <div className="mb-4">
            <FeedbackRow
              question="learned"
              label="Learned something"
              ends={["Nothing new", "A lot"]}
              value={learned}
              onChange={setLearned}
            />
          </div>
          <div className="flex items-center gap-3">
            <span ref={sendRef} className="inline-flex">
              <Button
                isDisabled={enjoyed === null || learned === null}
                isPending={give.isPending}
                onPress={send}
                className={cn(!reduced && "hover:-translate-y-px")}
              >
                Send
              </Button>
            </span>
            <span className="text-muted-foreground text-xs">Anonymous</span>
          </div>
        </div>
      ) : (
        <SentLine
          mine={feedback.mine}
          closesAt={feedback.closesAt}
          open={open}
          onChange={change}
          animate={folded && !reduced}
        />
      )}
    </div>
  );
}
