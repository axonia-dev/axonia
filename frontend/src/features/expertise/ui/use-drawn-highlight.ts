import { useEffect, useState } from "react";

import {
  type DrawnHighlight,
  transitionFor,
} from "@/features/expertise/ui/echo-transition";
import type { MapHighlight } from "@/features/expertise/ui/map-highlight";
import type { MapSearch } from "@/features/expertise/ui/map-search";
import { useMediaQuery } from "@/lib/use-media-query";

/** Whether the map played an echo for a term, reported once it has decided. */
export interface EchoReport {
  term: string;
  echoes: boolean;
}

/** An echo drops from a lit mark, so a term lighting none, such as an area offered and never practised, only fades. */
const echoes = (drawn: DrawnHighlight, found: MapSearch | null) =>
  drawn.transition === "echo" && found !== null && found.lit.size > 0;

/**
 * What the drawing shows and how it gets there, decided in render from what it showed before. Whether it echoes is
 * reported up, since the people listed beside the map wait for an echo and for nothing else.
 */
export function useDrawnHighlight({
  lit,
  settled,
  found,
  onEchoesChange,
}: Readonly<{
  lit: MapHighlight;
  settled: string;
  found: MapSearch | null;
  onEchoesChange: (played: EchoReport) => void;
}>): DrawnHighlight {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  // The previous term is kept in state rather than a ref so the transition is decided in render, from what was shown.
  const [shown, setShown] = useState<{ term: string; drawn: DrawnHighlight }>({
    term: settled,
    drawn: { highlight: lit, transition: "snap" },
  });
  if (shown.drawn.highlight !== lit) {
    setShown({
      term: settled,
      drawn: {
        highlight: lit,
        transition: transitionFor({
          from: shown.term,
          to: settled,
          found,
          reduced,
        }),
      },
    });
  }
  const played = echoes(shown.drawn, found);
  useEffect(() => {
    onEchoesChange({ term: shown.term, echoes: played });
  }, [shown, played, onEchoesChange]);
  return shown.drawn;
}
