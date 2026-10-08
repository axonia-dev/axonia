import { type MouseEvent, useEffect, useReducer, useRef } from "react";

type PickState = "none" | "pending" | "armed";

/**
 * A pick leaves focus in the field, so no focus event selects its text when it is clicked again.
 * "pending" until the render the pick causes commits, whatever value it carries (`notePick`
 * forces that render even when the value stays); any later change of value disarms it.
 */
export function useReselectAfterPick(value: string) {
  const pickRef = useRef<PickState>("none");
  const [, rerender] = useReducer((picks: number) => picks + 1, 0);
  const committedValueRef = useRef(value);

  useEffect(() => {
    const valueChanged = committedValueRef.current !== value;
    committedValueRef.current = value;
    if (pickRef.current === "pending") {
      pickRef.current = "armed";
    } else if (valueChanged) {
      pickRef.current = "none";
    }
  });

  return {
    notePick: () => {
      pickRef.current = "pending";
      rerender();
    },
    disarm: () => {
      pickRef.current = "none";
    },
    selectOnClick: (event: MouseEvent<HTMLInputElement>) => {
      if (pickRef.current === "none") {
        return;
      }
      pickRef.current = "none";
      const input = event.currentTarget;
      // A drag has already chosen what to select.
      if (input.selectionStart === input.selectionEnd) {
        input.select();
      }
    },
  };
}
