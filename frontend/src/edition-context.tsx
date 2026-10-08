import { createContext, type ReactNode, useContext } from "react";

import type { EditionSlots } from "@/edition";
import { openSlots } from "@/open-slots";

const EditionContext = createContext<EditionSlots>(openSlots);

export function EditionProvider({
  edition,
  children,
}: Readonly<{ edition: EditionSlots; children: ReactNode }>) {
  return <EditionContext value={edition}>{children}</EditionContext>;
}

export function useEdition(): EditionSlots {
  return useContext(EditionContext);
}
