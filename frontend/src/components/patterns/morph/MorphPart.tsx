import { type ReactElement, ViewTransition } from "react";

import { MORPH } from "@/components/patterns/morph/morph-history";

export type MorphPartName =
  "banner" | "badge" | "name" | "surface" | "title" | "pill";

function morphClass(part: MorphPartName): string {
  switch (part) {
    case "banner":
      return "morph-cover";
    case "surface":
      return "morph-surface";
    case "name":
    case "title":
      return "morph-text";
    case "badge":
    case "pill":
      return "morph-part";
  }
}

export const communityMorphId = (slug: string) => `community-${slug}`;
export const sessionMorphId = (id: number) => `session-${id}`;

export function morphName(part: MorphPartName, id: string): string {
  return `morph-${part}-${id}`.replaceAll(/[^\w-]/g, "_");
}

export function MorphPart({
  part,
  id,
  children,
}: Readonly<{
  part: MorphPartName;
  id: string | undefined;
  children: ReactElement;
}>): ReactElement {
  return (
    <ViewTransition
      {...(id !== undefined && { name: morphName(part, id) })}
      default="none"
      share={
        id === undefined
          ? "none"
          : { [MORPH]: morphClass(part), default: "none" }
      }
    >
      {children}
    </ViewTransition>
  );
}
