import type { MouseEvent, ReactNode } from "react";

import { recordOpening } from "@/features/knowledge/api/record-opening";

interface Props {
  artifactId: number;
  url: string;
  className?: string;
  children: ReactNode;
}

/** The middle mouse button, which opens a link in a new tab. `auxclick` also fires for the right
 *  button, where opening a context menu is not an opening of anything. */
const MIDDLE_BUTTON = 1;

/**
 * Every outbound artifact link in the product, so there is one place the opening is counted and one
 * place a reader's link behaves the same way.
 *
 * The href stays the artifact's real URL. It is deliberately not routed through a redirect the
 * product owns: hovering has to show where the link actually goes, a copied link has to keep
 * working, and the library must stay readable when axonia is not.
 */
export function ArtifactLink({
  artifactId,
  url,
  className,
  children,
}: Readonly<Props>) {
  function countMiddleClick(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button === MIDDLE_BUTTON) {
      recordOpening(artifactId);
    }
  }

  return (
    <a
      href={url}
      target="_blank"
      rel="noreferrer"
      className={className}
      onClick={() => recordOpening(artifactId)}
      onAuxClick={countMiddleClick}
    >
      {children}
    </a>
  );
}
