/** Past this a broad term would fill the panel with a wall of buttons; the count says how many there are in all. */
const MAX_CHIPS = 8;

/** Several areas matched, so no list of people: the term names them and a choice sets the map on one. */
export function MatchedAreasLine({
  tags,
  onChoose,
}: Readonly<{ tags: readonly string[]; onChoose: (tag: string) => void }>) {
  const rest = tags.length - MAX_CHIPS;
  return (
    <div className="flex flex-wrap items-center gap-1.5 text-sm">
      <span className="text-muted-foreground">
        Matches {tags.length} areas:
      </span>
      {tags.slice(0, MAX_CHIPS).map((tag) => (
        <button
          key={tag}
          type="button"
          onClick={() => onChoose(tag)}
          className="border-border-strong text-foreground hover:border-primary hover:text-primary-strong focus-visible:ring-focus-ring cursor-pointer rounded-full border px-2.5 py-0.5 text-xs font-medium outline-none focus-visible:ring-2"
        >
          {tag}
        </button>
      ))}
      {rest > 0 && (
        <span className="text-muted-foreground">and {rest} more</span>
      )}
    </div>
  );
}
