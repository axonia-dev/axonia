import { useEffect, useRef } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type {
  MapContribution,
  MapPerson,
} from "@/features/expertise/domain/expertise";
import { EvidenceRow } from "@/features/expertise/ui/evidence-row";
import { userHue } from "@/lib/user-hue";

/**
 * What one mark opens to. It sits in normal document flow, never over the canvas: a panel floating above the
 * drawing slides under the browser's own chrome at viewport sizes nobody tested, and nothing in flow can.
 *
 * The byline names the author and the area; the evidence row below names the kind, the title and the date, in the
 * exact words the evidence list uses everywhere else. Nothing here restates what that row already says.
 */
export function ContributionStrip({
  contribution,
  author,
}: Readonly<{
  contribution: MapContribution | null;
  author: MapPerson | null;
}>) {
  const panel = useRef<HTMLElement>(null);
  const signalId = contribution?.signalId ?? null;

  // The strip sits under the drawing in normal flow, which is what keeps it out from under the browser's own
  // chrome, and on a tall drawing that puts it below the fold. A reader who clicks a mark has to see what the
  // click did, so the answer is brought to them rather than the strip being floated over the picture.
  useEffect(() => {
    if (signalId !== null) {
      panel.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  }, [signalId]);

  if (contribution === null) return null;

  const name = author?.displayName ?? "Unknown member";

  return (
    <section
      ref={panel}
      data-testid="contribution-strip"
      className="border-border bg-surface mt-3 rounded-md border p-3"
    >
      <div className="flex items-center gap-3">
        <MemberAvatar
          name={name}
          hue={userHue(contribution.userId)}
          subject={null}
          src={author?.avatarUrl ?? null}
          shape="circle"
          className="size-9 text-xs"
        />
        <div className="min-w-0 flex-1">
          <p className="text-foreground truncate text-sm font-semibold">
            {name}
          </p>
          <p className="text-muted-foreground truncate text-xs">
            {contribution.canonicalTag}
          </p>
        </div>
      </div>
      <ul className="divide-border mt-1 divide-y">
        <EvidenceRow item={contribution.evidence} />
      </ul>
    </section>
  );
}
