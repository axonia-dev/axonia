import { type ReactNode, useId } from "react";

/**
 * One named section of the picker: a heading naming the group, and a labelled
 * `role="group"` grid of pickable entries below it. Shared by the organization's
 * own reactions and every standard catalog group, so a reader who has found one
 * has found the other.
 */
export function EmojiGroupGrid({
  heading,
  children,
}: Readonly<{ heading: string; children: ReactNode }>) {
  const headingId = useId();
  return (
    <div className="flex flex-col gap-1">
      <h3
        id={headingId}
        className="text-muted-foreground px-1 text-[10px] font-bold tracking-wider uppercase"
      >
        {heading}
      </h3>
      {/* Eight columns, which is what the common set is sized to: at seven it wrapped an
       *  orphan onto a second row and the group read as unfinished. */}
      <div
        role="group"
        aria-labelledby={headingId}
        className="grid grid-cols-8 gap-1"
      >
        {children}
      </div>
    </div>
  );
}
