import { PersonHoverCard } from "@/components/patterns/PersonHoverCard";
import type { User } from "@/features/identity/domain/user";

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
  label: string;
}

const DEFAULT_MAX = 5;

/** Overlapping faces, each revealing its person's card, then a chip for those not drawn. */
export function PersonStack({
  people,
  max = DEFAULT_MAX,
  total = people.length,
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
        />
      ))}
      {remainder > 0 && (
        <span
          aria-label={`${remainder} more`}
          role="img"
          className="border-surface-sunken bg-surface-sunken text-muted-foreground inline-flex size-7 items-center justify-center rounded-full border-2 text-xs font-bold"
        >
          +{remainder}
        </span>
      )}
    </span>
  );
}
