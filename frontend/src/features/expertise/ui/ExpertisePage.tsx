import { useSearchParams } from "react-router-dom";

import { SegmentedControl } from "@/components/ui/SegmentedControl";
import {
  type ExpertiseViewMode,
  parseViewMode,
  storedViewMode,
  storeViewMode,
} from "@/features/expertise/ui/expertise-preferences";
import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";
import { ExpertiseSearchView } from "@/features/expertise/ui/ExpertiseSearchView";
import { cn } from "@/lib/cn";

const VIEW_OPTIONS = [
  { id: "search", label: "Search" },
  { id: "map", label: "Map" },
] as const;

/**
 * Two views of one page, the way the schedule holds its timeline and its calendar: the URL decides, a sticky
 * preference answers when it says nothing, and search is what a member lands on. The views share no controls, which
 * is why the map's reading selector cannot appear over the search.
 */
export function ExpertisePage() {
  const [params, setParams] = useSearchParams();
  const view: ExpertiseViewMode =
    parseViewMode(params.get("view")) ?? storedViewMode() ?? "search";

  function choose(next: string) {
    const mode = parseViewMode(next) ?? "search";
    storeViewMode(mode);
    setParams(
      (prev) => {
        const updated = new URLSearchParams(prev);
        updated.set("view", mode);
        return updated;
      },
      { replace: true },
    );
  }

  return (
    // The shell gives this route the wide width the map needs. The search view is a list and reads at the width
    // every other page reads at, so it narrows itself back here rather than the shell having to know which of the
    // two views is showing - the URL alone cannot tell it, since a stored preference answers when the URL says
    // nothing.
    <section
      className={cn(
        "mx-auto flex w-full flex-col gap-4",
        view === "map" ? "max-w-[1600px]" : "max-w-3xl",
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-foreground font-display text-xl font-semibold">
          Expertise
        </h1>
        <SegmentedControl
          label="Expertise view"
          hideLabel
          value={view}
          options={VIEW_OPTIONS}
          onChange={choose}
        />
      </div>
      {view === "map" ? <ExpertiseMapView /> : <ExpertiseSearchView />}
    </section>
  );
}
