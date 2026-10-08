import { useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";

import { EmptyState } from "@/components/patterns/EmptyState";
import { useExpertiseMap } from "@/features/expertise/api/use-expertise-map";
import { storeViewMode } from "@/features/expertise/ui/expertise-preferences";
import { buildReading } from "@/features/expertise/ui/expertise-readings";
import { highlightFor } from "@/features/expertise/ui/map-highlight";
import { MapReadings } from "@/features/expertise/ui/MapReadings";
import { groupsFor } from "@/features/expertise/ui/named-area-grouping";
import {
  type PanelSelection,
  ReadingPanel,
} from "@/features/expertise/ui/ReadingPanel";
import { useNow } from "@/lib/use-wake-at";

/**
 * The map and the readings beside it. Below md the readings come first and the drawing follows under them: the lists
 * carry every fact, so a phone opens on an answer, and the map stays one scroll away rather than out of reach. The
 * panel leads in the source as well, so a keyboard reaches it before the overlay's buttons; md lifts the map back left.
 */
export function ExpertiseMapView() {
  const { slug = "" } = useParams();
  const [, setParams] = useSearchParams();
  const [reading, setReading] = useState<PanelSelection>("neighbourhoods");
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
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

  return (
    <div className="border-border bg-surface grid grid-cols-1 overflow-hidden rounded-2xl border shadow-sm md:grid-cols-[minmax(0,1fr)_296px]">
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
      />
      <div className="border-border border-t md:order-first md:border-t-0">
        <MapReadings
          slug={slug}
          topics={topics}
          links={links}
          groups={groups}
          highlight={highlight}
        />
      </div>
    </div>
  );
}
