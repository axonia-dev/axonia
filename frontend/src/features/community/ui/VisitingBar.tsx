import { DoorOpen } from "lucide-react";
import { type ReactNode, useState } from "react";

import { useMyMembership } from "@/features/community/api/use-membership";
import { useDepartForCeremony } from "@/features/community/ui/ceremony/use-depart-for-ceremony";
import { JoinAction } from "@/features/community/ui/JoinAction";
import {
  useVisitingJoin,
  useVisitingWelcome,
  VisitingJoinProvider,
} from "@/features/community/ui/visiting-join-context";
import { cn } from "@/lib/cn";

/** Shown once the person is seen visiting; open until joined and any welcome has ended. */
function useVisitingStage(slug: string): {
  shown: boolean;
  open: boolean;
  welcomeInPlace: (() => void) | undefined;
} {
  const { data: membership, isError } = useMyMembership(slug);
  const inPlace = useVisitingJoin();
  const welcoming = useVisitingWelcome();
  const visitor = isError || membership === null;
  const [shown, setShown] = useState(visitor);
  const [awaitingWelcome, setAwaitingWelcome] = useState(false);
  if (visitor && !shown) setShown(true);
  if (awaitingWelcome && welcoming) setAwaitingWelcome(false);
  return {
    shown,
    open: visitor || welcoming || awaitingWelcome,
    welcomeInPlace:
      inPlace === undefined
        ? undefined
        : () => {
            setAwaitingWelcome(true);
            inPlace();
          },
  };
}

/**
 * Tells a non-member they are only visiting, with Join at hand on every page of the community.
 * The overview's hero welcomes in place; anywhere else a join departs for the full welcome.
 * Once joined, the bar stays until the welcome ends, then folds away rather than jumping.
 */
function VisitingBar({
  name,
  slug,
  onDepart,
}: Readonly<{ name: string; slug: string; onDepart: () => Promise<void> }>) {
  const { shown, open, welcomeInPlace } = useVisitingStage(slug);
  if (!shown) return null;

  return (
    <div
      data-visiting={open ? "open" : "closed"}
      inert={!open}
      className={cn(
        "grid transition-opacity duration-300 motion-safe:transition-[grid-template-rows,opacity] motion-safe:ease-out",
        open
          ? "grid-rows-[minmax(0,1fr)] opacity-100"
          : "grid-rows-[minmax(0,0fr)] opacity-0",
      )}
    >
      <div className="min-h-0 overflow-hidden">
        <section
          aria-label="Membership"
          className="mb-6 flex items-center gap-3 rounded-lg border border-[color-mix(in_srgb,var(--primary)_22%,var(--border))] bg-[color-mix(in_srgb,var(--primary)_7%,var(--surface))] py-2 pr-2 pl-3.5"
        >
          <DoorOpen
            size={16}
            aria-hidden
            className="text-primary-ink flex-none"
          />
          <p className="text-muted-foreground min-w-0 flex-1 truncate text-sm">
            Visiting{" "}
            <span className="text-foreground font-semibold">{name}</span>
          </p>
          <JoinAction
            slug={slug}
            size="sm"
            onJoined={welcomeInPlace ?? onDepart}
          />
        </section>
      </div>
    </div>
  );
}

/**
 * The community's pages under the Visiting bar, which a page can hand its own welcome. The
 * departure lives here, not in the bar, because the bar unmounts as soon as the join lands.
 */
export function VisitingFrame({
  name,
  slug,
  children,
}: Readonly<{ name: string; slug: string; children: ReactNode }>) {
  const depart = useDepartForCeremony();
  return (
    <VisitingJoinProvider>
      <VisitingBar
        key={slug}
        name={name}
        slug={slug}
        onDepart={() => depart("welcome", slug)}
      />
      {children}
    </VisitingJoinProvider>
  );
}
