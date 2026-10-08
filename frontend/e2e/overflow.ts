import type { Page } from "@playwright/test";

/** Every element inside <main> whose right edge lands outside it: the ones that make the
 *  content area scroll sideways, named by their class list so a failure says which row to fix.
 *  `main` is the app's scroll container - the document itself never scrolls - so its own
 *  client box, not the viewport, is the edge that counts. */
export function overflowing(page: Page): Promise<string[]> {
  return page.locator("main").evaluate((main) => {
    const edge = main.getBoundingClientRect().left + main.clientWidth;
    return [...main.querySelectorAll("*")]
      .filter((element) => {
        const box = element.getBoundingClientRect();
        return box.width > 0 && box.height > 0 && box.right > edge + 1;
      })
      .map(
        (element) =>
          `<${element.tagName.toLowerCase()} class="${element.getAttribute("class") ?? ""}">`,
      );
  });
}
