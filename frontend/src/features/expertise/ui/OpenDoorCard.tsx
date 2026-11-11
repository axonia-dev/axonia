import { Button } from "react-aria-components";

import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import type { OpenArea } from "@/features/expertise/domain/expertise";
import { userHue } from "@/lib/user-hue";

const MAX_FACES = 4;

function peopleCount(count: number): string {
  return count === 1 ? "1 person" : `${count} people`;
}

/** One area on the shelf: its name, the ringed faces open on it, and how many they are. */
export function OpenDoorCard({
  area,
  isNew,
  onOpen,
}: Readonly<{ area: OpenArea; isNew: boolean; onOpen: () => void }>) {
  const count = peopleCount(area.people.length);
  return (
    <Button
      onPress={onOpen}
      aria-label={`${area.canonicalTag}, ${count} open to questions`}
      className="border-border bg-surface data-[focus-visible]:ring-focus-ring flex w-full min-w-0 cursor-pointer flex-col items-start gap-2.5 rounded-md border p-3.5 text-left outline-none data-[focus-visible]:ring-2 data-[hovered]:border-[color-mix(in_srgb,var(--primary)_45%,var(--border))] motion-safe:transition-[border-color,transform] motion-safe:duration-200 motion-safe:data-[hovered]:-translate-y-0.5"
    >
      <span className="font-display text-foreground max-w-full text-base font-semibold break-words">
        {area.canonicalTag}
      </span>
      <span className="flex w-full items-center justify-between gap-2">
        <MemberFacepile
          members={area.people.map((person) => ({
            id: person.userId,
            name: person.displayName ?? person.userId,
            hue: userHue(person.userId),
            src: person.avatarUrl,
          }))}
          max={MAX_FACES}
          size="size-7 text-[0.625rem]"
          open
        />
        {isNew && (
          <span className="text-primary-strong inline-flex shrink-0 items-center gap-1.5 text-[0.68rem] font-medium">
            <span
              aria-hidden="true"
              className="bg-focus-ring ring-primary/15 size-1.5 rounded-full ring-3"
            />
            New
          </span>
        )}
      </span>
      <span className="text-muted-foreground text-xs">{count}</span>
    </Button>
  );
}
