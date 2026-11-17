import { useEffect, useRef } from "react";

const FIELD = "input, textarea";
const TRIGGER = "[data-swap-trigger]";

/** An inline editor that replaces its trigger: opening moves focus to the first field, closing returns it to the trigger, so a keyboard never lands on the page body. Only a swap the person asked for moves focus. */
export function useSwapFocus<T extends HTMLElement>(open: boolean) {
  const box = useRef<T>(null);
  const swapped = useRef(false);
  useEffect(() => {
    if (!swapped.current) return;
    swapped.current = false;
    box.current?.querySelector<HTMLElement>(open ? FIELD : TRIGGER)?.focus();
  }, [open]);
  return {
    box,
    swap: () => {
      swapped.current = true;
    },
  };
}
