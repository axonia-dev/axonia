import { act } from "@testing-library/react";

/**
 * Switches the page's theme the way `ThemeProvider` does, and settles what watches it.
 *
 * Anything that resolves a colour out of the cascade rather than reading it through a class - the
 * WebGL drawings do, because there is no cascade inside a canvas - follows the switch through a
 * `MutationObserver`, and an observer delivers on a microtask. Without the awaited tick the
 * assertion runs on the frame before the repaint.
 */
export async function switchTheme(className: string): Promise<void> {
  await act(async () => {
    document.documentElement.className = className;
    await Promise.resolve();
  });
}
