/** Several areas matched, so no list of people: the term names them and a choice sets the map on one. */
export function MatchedAreasLine({
  tags,
  onChoose,
}: Readonly<{ tags: readonly string[]; onChoose: (tag: string) => void }>) {
  return (
    <div className="flex flex-wrap items-center gap-1.5 text-sm">
      <span className="text-muted-foreground">
        Matches {tags.length} areas:
      </span>
      {tags.map((tag) => (
        <button
          key={tag}
          type="button"
          onClick={() => onChoose(tag)}
          className="border-border-strong text-foreground hover:border-primary hover:text-primary-strong focus-visible:ring-focus-ring cursor-pointer rounded-full border px-2.5 py-0.5 text-xs font-medium outline-none focus-visible:ring-2"
        >
          {tag}
        </button>
      ))}
    </div>
  );
}
