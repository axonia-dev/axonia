import {
  PersonHoverCard,
  type Surface,
  surfaceBorder,
} from "@/components/patterns/PersonHoverCard";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

interface StackedPerson {
  id: string;
  name: string;
  user?: User | undefined;
  detail?: string | undefined;
}

interface PersonStackProps {
  people: readonly StackedPerson[];
  max?: number;
  /** How many people there are in all, when `people` holds only the first few. */
  total?: number;
  /** The background the stack sits on, which the separating border must match. */
  surface?: Surface;
  label: string;
}

const DEFAULT_MAX = 5;

/** Overlapping faces, each revealing its person's card, then a chip for those not drawn. */
export function PersonStack({
  people,
  max = DEFAULT_MAX,
  total = people.length,
  surface = "sunken",
  label,
}: Readonly<PersonStackProps>) {
  const remainder = total - Math.min(max, people.length);
  return (
    <span role="group" aria-label={label} className="flex -space-x-1.5">
      {people.slice(0, max).map((person) => (
        <PersonHoverCard
          key={person.id}
          name={person.name}
          user={person.user}
          detail={person.detail}
          surface={surface}
        />
      ))}
      {remainder > 0 && (
        <span
          aria-label={`${remainder} more`}
          role="img"
          className={cn(
            "text-muted-foreground inline-flex size-7 items-center justify-center rounded-full border-2 text-xs font-bold",
            surfaceBorder(surface),
            surface === "raised" ? "bg-surface" : "bg-surface-sunken",
          )}
        >
          +{remainder}
        </span>
      )}
    </span>
  );
}
