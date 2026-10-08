interface TagChipsProps {
  tags: string[];
  /** When given, each chip becomes a button that filters by its tag. */
  onTagClick?: ((tag: string) => void) | undefined;
}

/**
 * The one way a subject is shown: the tags of an artifact, and of a talk that was never proposed.
 * Both are the same thing to a reader, so both look the same.
 *
 * Inline flow rather than a flex row, so the chips wrap around a float in the row that hosts them:
 * a flex container is pushed below a float wholesale instead of flowing around it. `-mb-1` cancels
 * the last row's chip margin wherever the list is block-level.
 */
export function TagChips({ tags, onTagClick }: Readonly<TagChipsProps>) {
  if (tags.length === 0) {
    return null;
  }
  return (
    <ul className="-mb-1 inline">
      {tags.map((tag) => (
        <li key={tag} className="mr-1 mb-1 inline-block">
          {onTagClick ? (
            <button
              type="button"
              onClick={() => onTagClick(tag)}
              className="bg-surface border-border text-muted-foreground hover:text-primary hover:border-primary/40 cursor-pointer rounded-sm border px-2 py-0.5 text-xs transition-colors"
            >
              #{tag}
            </button>
          ) : (
            <span className="bg-surface border-border text-muted-foreground rounded-sm border px-2 py-0.5 text-xs">
              #{tag}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}
