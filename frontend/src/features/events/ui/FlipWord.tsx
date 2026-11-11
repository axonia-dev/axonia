import { useState } from "react";

import { rollIn, rollOut } from "@/features/events/ui/feedback-motion";

/** The word an answer reads as, rolling in like a counter as it changes. The visual word follows a
 *  preview; only the committed word is announced, so a pointer crossing the bars stays silent. */
export function FlipWord({
  word,
  spoken,
  still,
}: Readonly<{ word: string; spoken: string; still: boolean }>) {
  const [shown, setShown] = useState({ word, leaving: "" });
  if (shown.word !== word) setShown({ word, leaving: shown.word });
  return (
    <span className="text-data-1 relative block h-[18px] min-w-[90px] overflow-hidden text-right text-xs font-semibold">
      {!still && shown.leaving && (
        <span
          key={`out-${shown.leaving}`}
          ref={rollOut}
          aria-hidden="true"
          className="absolute top-0 right-0 block"
        >
          {shown.leaving}
        </span>
      )}
      <span
        key={word}
        ref={still ? undefined : rollIn}
        aria-hidden="true"
        data-word
        className="block"
      >
        {word}
      </span>
      <span aria-live="polite" className="sr-only">
        {spoken}
      </span>
    </span>
  );
}
