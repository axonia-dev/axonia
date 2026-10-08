import { type RefObject, useLayoutEffect, useState } from "react";

/** True once a read that failed has answered: the field that replaces its failure message then
 *  takes the focus that message's Try again held, rather than dropping it to the page. */
export function useAnsweredAfterFailing(failed: boolean): boolean {
  const [hasFailed, setHasFailed] = useState(failed);
  if (failed && !hasFailed) setHasFailed(true);
  return hasFailed && !failed;
}

/** The elements a failure message held the focus in when it went away. */
const droppedFrom = new WeakSet<Element>();

/** Run by a failure message: if it holds the focus as it goes, the element around it lost that
 *  focus, and the field that replaces the message in the same commit may take it. A layout
 *  effect's cleanup runs before the message leaves the document, so the focus is still inside it.
 *  The loss lasts only until the task that removed the message ends: that commit's passive
 *  effects have run by then, and a field whose read fails and answers later, under the same
 *  element, did not replace a message that held the focus. */
export function useReportsDroppedFocus(
  message: RefObject<HTMLElement | null>,
): void {
  useLayoutEffect(() => {
    const node = message.current;
    const around = node?.parentElement;
    if (node === null || around === null || around === undefined) return;
    return () => {
      if (!node.contains(document.activeElement)) return;
      droppedFrom.add(around);
      setTimeout(() => droppedFrom.delete(around));
    };
  }, [message]);
}

function droppedAround(element: HTMLElement): Element | null {
  for (let at = element.parentElement; at !== null; at = at.parentElement) {
    if (droppedFrom.has(at)) return at;
  }
  return null;
}

/** Focuses `element` only when a failure message around it went away holding the focus, as Try
 *  again does, and nothing has taken the focus since. A read that answers on its own, on a
 *  reconnect or an invalidation, leaves the focus where it is, on a field or on the page. */
export function focusIfDropped(element: HTMLElement | null): void {
  if (element === null) return;
  const held = document.activeElement;
  if (held !== null && held !== document.body) return;
  const around = droppedAround(element);
  if (around === null) return;
  droppedFrom.delete(around);
  element.focus();
}
