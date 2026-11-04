import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

import { paged } from "./community-home";

const PIXEL_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

export const group = (part: string, id: string) =>
  `::view-transition-group(morph-${part}-${id})`;

/**
 * Every morph group a view transition built since the document loaded or since `forgetMorphs`,
 * read at the transition's `ready`, the one moment its groups are sure to exist. Sampling frames
 * instead misses them on a slow runner, where a morph's groups can live between two frames.
 */
export async function recordMorphs(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const record = globalThis as { morphsSeen?: Set<string> };
    record.morphsSeen = new Set();
    const start = document.startViewTransition.bind(document);
    document.startViewTransition = ((options: StartViewTransitionOptions) => {
      const transition = start(options);
      transition.ready.then(
        () => {
          for (const animation of document.getAnimations()) {
            const pseudo =
              (animation.effect as KeyframeEffect | null)?.pseudoElement ?? "";
            if (pseudo.startsWith("::view-transition-group(morph-")) {
              record.morphsSeen?.add(pseudo);
            }
          }
        },
        () => undefined,
      );
      return transition;
    }) as typeof document.startViewTransition;
  });
}

export function morphsSeen(page: Page): Promise<string[]> {
  return page.evaluate(() => [
    ...((globalThis as { morphsSeen?: Set<string> }).morphsSeen ?? []),
  ]);
}

async function forgetMorphs(page: Page): Promise<void> {
  await page.evaluate(() => {
    (globalThis as { morphsSeen?: Set<string> }).morphsSeen?.clear();
  });
}

export async function groupsDuring(
  page: Page,
  act: () => Promise<unknown>,
  expected: string[],
): Promise<void> {
  await forgetMorphs(page);
  await act();
  await expect
    .poll(() => morphsSeen(page), { timeout: 2000 })
    .toEqual(expect.arrayContaining(expected));
}

/** Settles a few frames past the destination, so a morph that started is sure to be recorded. */
export async function noGroupsDuring(
  page: Page,
  act: () => Promise<unknown>,
  arrived: () => Promise<void>,
): Promise<void> {
  await forgetMorphs(page);
  await act();
  await arrived();
  await page.evaluate(
    () =>
      new Promise((done) => {
        setTimeout(done, 600);
      }),
  );
  expect(await morphsSeen(page)).toEqual([]);
}

/** The lobby lists its two sections from one path, told apart by the membership parameter. */
export async function stubLobby(
  page: Page,
  sections: { joined: unknown[]; explore: unknown[] },
): Promise<void> {
  await page.route(
    (url) => url.pathname === "/axonia/communities",
    (route) => {
      const { searchParams } = new URL(route.request().url());
      const joined = searchParams.get("membership") === "JOINED";
      const content = joined ? sections.joined : sections.explore;
      return route.fulfill({ json: paged(content) });
    },
  );
}

export async function stubImages(page: Page): Promise<void> {
  await page.route(
    (url) => url.pathname.startsWith("/morph-"),
    (route) => route.fulfill({ contentType: "image/png", body: PIXEL_PNG }),
  );
}
