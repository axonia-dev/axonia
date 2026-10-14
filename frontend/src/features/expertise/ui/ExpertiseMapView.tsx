import { useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";

import { EmptyState } from "@/components/patterns/EmptyState";
import { useExpertiseMap } from "@/features/expertise/api/use-expertise-map";
import { storeViewMode } from "@/features/expertise/ui/expertise-preferences";
import { buildReading } from "@/features/expertise/ui/expertise-readings";
import { highlightFor } from "@/features/expertise/ui/map-highlight";
import { areasMatching } from "@/features/expertise/ui/map-search";
import { MapReadings } from "@/features/expertise/ui/MapReadings";
import { groupsFor } from "@/features/expertise/ui/named-area-grouping";
import {
  type PanelSelection,
  ReadingPanel,
} from "@/features/expertise/ui/ReadingPanel";
import { TermAnswer } from "@/features/expertise/ui/TermAnswer";
import type { EchoReport } from "@/features/expertise/ui/use-drawn-highlight";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { useMediaQuery } from "@/lib/use-media-query";
import { useNow } from "@/lib/use-wake-at";

/** Long enough that a term is matched once rather than per keystroke: every settled term repaints the ground, which
 *  is a heightfield of tens of thousands of shaded vertices. */
const SEARCH_DEBOUNCE_MS = 180;

/** Where the readings fit beside a drawing of real width: beside it below this, the drawing was 160px wide at 768
 *  and 414px at 1024. */
const BESIDE = "(min-width: 1280px)";

const NO_AREAS: readonly string[] = [];

/**
 * The map and the readings beside it. Below md the readings come first and the drawing follows under them: the lists
 * carry every fact, so a phone opens on an answer, and the map stays one scroll away rather than out of reach. The
 * panel leads in the source as well, so a keyboard reaches it before the overlay's buttons; md lifts the map above
 * them, and xl stands them side by side. While they are stacked, what a term answers is listed under the drawing,
 * below the box it was typed in, and the readings above stay as they were.
 */
export function ExpertiseMapView() {
  const { slug = "" } = useParams();
  const [, setParams] = useSearchParams();
  const [reading, setReading] = useState<PanelSelection>("neighbourhoods");
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [term, setTerm] = useState("");
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const beside = useMediaQuery(BESIDE);
  const [played, setPlayed] = useState<EchoReport | null>(null);
  const settled = useDebouncedValue(term, SEARCH_DEBOUNCE_MS);
  const { data, isLoading, isError } = useExpertiseMap(slug);

  const topics = useMemo(() => data?.topics ?? [], [data]);
  const links = useMemo(() => data?.links ?? [], [data]);
  const namedAreas = useMemo(() => data?.namedAreas ?? [], [data]);
  const groups = useMemo(
    () => groupsFor(topics, links, namedAreas),
    [topics, links, namedAreas],
  );
  // Built here rather than in the panel, because the map is drawn from the same answer: built twice, the two copies
  // would be measured against two different "now"s and the drawing could light an area the list beside it omits.
  const now = useNow();
  const built = useMemo(
    () =>
      reading === "neighbourhoods" ? null : buildReading(reading, topics, now),
    [reading, topics, now],
  );
  const highlight = useMemo(
    () => highlightFor(built, selectedTag),
    [built, selectedTag],
  );

  // A box cleared, or emptied by choosing one of the areas, matches nothing at once rather than a debounce later.
  const cleared = term.trim() === "" || settled.trim() === "";
  const matchedAreas = useMemo(
    () => (cleared ? [] : areasMatching(settled, topics)),
    [cleared, settled, topics],
  );

  // Until the map has decided for this term, the people wait as if it echoes: a wait cut short reads better than rows
  // that jump in ahead of the picture.
  const echoing = played?.term === settled ? played.echoes : !reduced;

  function seeWhoKnows(tag: string) {
    storeViewMode("search");
    setParams(
      (prev) => {
        const updated = new URLSearchParams(prev);
        updated.set("view", "search");
        updated.set("term", tag);
        return updated;
      },
      { replace: true },
    );
  }

  function chooseMatchedArea(tag: string) {
    setReading("neighbourhoods");
    setSelectedTag(tag);
    setTerm("");
  }

  // The selection goes too, including back onto "Neighbourhoods". Kept, it put a reader who chose an area, went to
  // a reading and came back on the area's own page with no way out of it, which is what "no way back" was.
  function chooseReading(next: PanelSelection) {
    setReading(next);
    setSelectedTag(null);
  }

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading topics…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load topics.
      </p>
    );
  }
  if (topics.length === 0) {
    return (
      <EmptyState
        title="Nothing shared yet"
        description="As members host sessions and share artifacts, the topics this community knows show up here."
      />
    );
  }

  const answer = (
    <TermAnswer
      slug={slug}
      matchedAreas={matchedAreas}
      onSeeWhoKnows={seeWhoKnows}
      onChooseMatchedArea={chooseMatchedArea}
      echoing={echoing}
    />
  );

  return (
    <div className="border-border bg-surface grid grid-cols-1 overflow-hidden rounded-2xl border shadow-sm xl:grid-cols-[minmax(0,1fr)_296px]">
      <ReadingPanel
        topics={topics}
        links={links}
        groups={groups}
        reading={reading}
        built={built}
        onReadingChange={chooseReading}
        selectedTag={selectedTag}
        onSelectTag={setSelectedTag}
        onSeeWhoKnows={seeWhoKnows}
        matchedAreas={beside ? matchedAreas : NO_AREAS}
        slug={slug}
        echoing={echoing}
        onChooseMatchedArea={chooseMatchedArea}
      />
      <div className="border-border border-t md:order-first md:border-t-0">
        <MapReadings
          slug={slug}
          topics={topics}
          links={links}
          groups={groups}
          highlight={highlight}
          term={term}
          onTermChange={setTerm}
          settled={settled}
          onEchoesChange={setPlayed}
          answer={beside ? null : answer}
        />
      </div>
    </div>
  );
}
