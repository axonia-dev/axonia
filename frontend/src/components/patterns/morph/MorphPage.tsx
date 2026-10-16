import { type ReactElement, ViewTransition } from "react";

import { MORPH } from "@/components/patterns/morph/morph-history";

const ON_MORPH = { [MORPH]: "morph-page", default: "none" };

/** React cancels the root crossfade when nothing outside a boundary moved, so the page is its own. */
export function MorphPage({
  children,
}: Readonly<{ children: ReactElement }>): ReactElement {
  return (
    <ViewTransition
      name="morph-page"
      default="none"
      update={ON_MORPH}
      share={ON_MORPH}
    >
      {children}
    </ViewTransition>
  );
}
