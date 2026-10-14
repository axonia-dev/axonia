import { type RefObject, useEffect } from "react";

import { scrollMovesTarget } from "./stage-frame";

/** Ends the stage early: any key, and in place also a scroll that moves the card or a resize. */
export function useStageExits(
  finish: () => void,
  inPlace: boolean,
  target: RefObject<HTMLElement | null>,
): void {
  useEffect(() => {
    const skip = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey) {
        return;
      }
      if (!inPlace) {
        event.preventDefault();
        event.stopPropagation();
      }
      finish();
    };
    window.addEventListener("keydown", skip, true);
    return () => window.removeEventListener("keydown", skip, true);
  }, [finish, inPlace]);

  useEffect(() => {
    if (!inPlace) {
      return undefined;
    }
    const scrolled = (event: Event) => {
      if (scrollMovesTarget(event.target, target.current)) {
        finish();
      }
    };
    window.addEventListener("scroll", scrolled, {
      passive: true,
      capture: true,
    });
    window.addEventListener("resize", finish);
    return () => {
      window.removeEventListener("scroll", scrolled, { capture: true });
      window.removeEventListener("resize", finish);
    };
  }, [finish, inPlace, target]);
}
