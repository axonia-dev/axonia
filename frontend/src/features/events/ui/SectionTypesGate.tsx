import type { QueryObserverResult } from "@tanstack/react-query";
import { type ReactNode, useEffect, useRef } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import type { SectionType } from "@/features/events/domain/section-type";
import { failedToLoad } from "@/lib/failed-to-load";
import {
  focusIfDropped,
  useAnsweredAfterFailing,
} from "@/lib/use-answered-after-failing";

export type SectionTypesRead = Pick<
  QueryObserverResult<SectionType[]>,
  "data" | "errorUpdateCount" | "isFetching" | "refetch"
>;

interface Props {
  sectionTypes: SectionTypesRead;
  children: (sectionTypes: SectionType[]) => ReactNode;
}

/** A structure editor once the section types are in hand: until then it would read a pending or
 *  failed read as none, and ask for a section type that may already exist. When Try again brings
 *  them, the editor takes the focus that Try again held. */
export function SectionTypesGate({ sectionTypes, children }: Readonly<Props>) {
  const failed = failedToLoad(sectionTypes);
  const recovered = useAnsweredAfterFailing(failed);
  const editor = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (recovered) focusIfDropped(editor.current);
  }, [recovered]);

  if (failed) {
    return (
      <LoadFailed
        inline
        message="Could not load the section types."
        query={sectionTypes}
      />
    );
  }
  if (sectionTypes.data === undefined) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  return (
    <div
      ref={editor}
      role="group"
      aria-label="Structure"
      tabIndex={-1}
      className="outline-none"
    >
      {children(sectionTypes.data)}
    </div>
  );
}
