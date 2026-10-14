import { SearchField } from "@/components/ui/SearchField";
import {
  type MapSearch,
  matchSummary,
} from "@/features/expertise/ui/map-search";

/**
 * The box that picks things out of the drawing, and the one line that says what it found.
 *
 * The count is not decoration. The drawing answers by setting everything else back, so a term that matched nothing
 * and a term that matched one mark behind the reader's shoulder look the same from here; the words say which.
 *
 * The count sits in a slot of fixed width, held open while the box is empty and never wrapped. The toolbar wraps by
 * what is in it, and the drawing below is sized from what the toolbar leaves, so a count that changed the row's
 * width could rebuild the scene mid-animation.
 */
export function MapSearchField({
  term,
  onTermChange,
  found,
}: Readonly<{
  term: string;
  onTermChange: (next: string) => void;
  /** What the term settled on, or null while the box is empty. */
  found: MapSearch | null;
}>) {
  return (
    <div className="flex items-center gap-2">
      <SearchField
        label="Find on the map"
        value={term}
        onChange={onTermChange}
        placeholder="Find on the map"
        className="w-44"
      />
      <div className="w-44 shrink-0">
        {found !== null && (
          <p
            role="status"
            className="text-muted-foreground text-xs whitespace-nowrap"
          >
            {matchSummary(found)}
          </p>
        )}
      </div>
    </div>
  );
}
