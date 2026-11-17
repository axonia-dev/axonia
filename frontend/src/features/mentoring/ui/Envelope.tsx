import { useEffect, useRef, useState } from "react";
import { Button as AriaButton } from "react-aria-components";

import { useOpenWords } from "@/features/mentoring/api/use-mentoring";
import type { Words } from "@/features/mentoring/domain/mentoring";
import { HAND } from "@/features/mentoring/ui/handwriting";
import { pairingDates } from "@/features/mentoring/ui/mentoring-dates";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

type Stage = "sealed" | "opening" | "open";

/** The paper's rise; the envelope opens after it even when its transitionend never comes. */
const OPENING_MS = 700;

function From({ words }: Readonly<{ words: Words }>) {
  return (
    <div className="flex items-center gap-2.5">
      <PartnerFace
        userId={words.menteeId}
        name={words.menteeName}
        avatarUrl={words.menteeAvatarUrl}
        className="size-8 text-xs"
      />
      <div className="min-w-0">
        <p className="text-foreground text-sm font-semibold wrap-anywhere">
          {personName(words.menteeName, words.menteeId)}
        </p>
        <p className="text-muted-foreground text-xs">
          {words.communityName} · {pairingDates(words.startedAt, words.endedAt)}
        </p>
      </div>
    </div>
  );
}

function Letter({
  body,
  arriving,
  focused,
}: Readonly<{ body: string; arriving: boolean; focused: boolean }>) {
  const letter = useRef<HTMLParagraphElement>(null);
  useEffect(() => {
    if (focused) letter.current?.focus();
  }, [focused]);
  return (
    <p
      ref={letter}
      tabIndex={-1}
      className={cn(
        "outline-none",
        "bg-paper border-paper-edge text-ink-sepia -rotate-1 rounded-md border px-4 py-3 text-lg leading-7 wrap-anywhere whitespace-pre-line shadow-md",
        HAND,
        arriving && "motion-safe:animate-[memory-arrive_0.6s_ease-out_both]",
      )}
    >
      {body}
    </p>
  );
}

/**
 * Starts where an open letter starts, so the two line up side by side. Opening lifts the paper and
 * folds the flap back no more than 20px past the top, inside the panel's padding and the row gap,
 * so neither ever covers the guild's name or the row above.
 */
function Sealed({
  name,
  opening,
  onOpen,
  onOpened,
}: Readonly<{
  name: string;
  opening: boolean;
  onOpen: () => void;
  onOpened: () => void;
}>) {
  return (
    <div
      data-stage={opening ? "opening" : "sealed"}
      className="relative h-[144px] max-w-sm [perspective:600px]"
    >
      <div className="absolute inset-0 rounded-[10px] bg-[color-mix(in_srgb,var(--primary)_55%,#b2386a)]" />
      <div
        aria-hidden
        onTransitionEnd={onOpened}
        className={cn(
          "bg-paper absolute inset-x-[18px] top-5 h-[120px] rounded-md transition-transform duration-[600ms] ease-[cubic-bezier(.3,1.4,.5,1)]",
          opening && "-translate-y-8",
        )}
      />
      <div className="absolute inset-0 rounded-[10px] bg-[color-mix(in_srgb,var(--primary)_75%,#b2386a)] [clip-path:polygon(0_0,50%_55%,100%_0,100%_100%,0_100%)]" />
      <div
        className={cn(
          "absolute inset-x-0 top-0 h-16 origin-top rounded-t-[10px] bg-[color-mix(in_srgb,var(--primary)_90%,#000)] transition-transform duration-500 [clip-path:polygon(0_0,100%_0,50%_100%)]",
          opening && "[transform:rotateX(180deg)_scaleY(.2)]",
        )}
      />
      <span
        aria-hidden
        className={cn(
          "bg-warning absolute top-[54px] left-1/2 z-10 grid size-9 -translate-1/2 place-items-center rounded-full text-base transition-opacity duration-300",
          opening && "opacity-0",
        )}
      >
        🌻
      </span>
      <AriaButton
        aria-label={`Open the words from ${name}`}
        aria-disabled={opening}
        onPress={onOpen}
        className="focus-visible:ring-focus-ring absolute inset-0 z-20 cursor-pointer rounded-[10px] outline-none focus-visible:ring-2 disabled:cursor-default"
      />
    </div>
  );
}

/**
 * A mentee's words, sealed until the mentor opens them. Opening plays once and is remembered by
 * the server; changed words arrive sealed again. Under reduced motion it opens at once.
 */
export function Envelope({ words }: Readonly<{ words: Words }>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const openWords = useOpenWords();
  const [stage, setStage] = useState<Stage>(
    words.openedAt === null ? "sealed" : "open",
  );
  const [arriving, setArriving] = useState(false);
  const [opened, setOpened] = useState(false);
  const name = personName(words.menteeName, words.menteeId);
  useEffect(() => {
    if (stage !== "opening") return;
    const fallback = window.setTimeout(() => setStage("open"), OPENING_MS);
    return () => window.clearTimeout(fallback);
  }, [stage]);

  /** A press while the envelope is already opening is ignored, the button stays focusable. */
  function open() {
    if (stage !== "sealed") return;
    openWords.mutate(words.pairingId, { onError: onFailure });
    setArriving(!reduced);
    setOpened(true);
    setStage(reduced ? "open" : "opening");
  }

  return (
    <li className="flex flex-col gap-3">
      {stage === "open" ? (
        <Letter body={words.body} arriving={arriving} focused={opened} />
      ) : (
        <Sealed
          name={name}
          opening={stage === "opening"}
          onOpen={open}
          onOpened={() => setStage("open")}
        />
      )}
      <From words={words} />
    </li>
  );
}
