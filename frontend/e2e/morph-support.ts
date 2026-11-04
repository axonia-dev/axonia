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
 * A log of each transition's fate goes with them, so a morph that never played says why.
 */
export async function recordMorphs(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const record = globalThis as {
      morphsSeen?: Set<string>;
      morphLog?: string[];
    };
    record.morphsSeen = new Set();
    record.morphLog = [];
    const log = (line: string) =>
      record.morphLog?.push(`${performance.now().toFixed(0)}ms ${line}`);
    const pushState = history.pushState.bind(history);
    history.pushState = (...args: Parameters<History["pushState"]>) => {
      log(`pushState ${String(args[2])}`);
      pushState(...args);
    };
    const named = () =>
      [
        ...document.querySelectorAll<HTMLElement>(
          "[style*='view-transition-name']",
        ),
      ]
        .map((element) => element.style.viewTransitionName)
        .join(" ");
    const groups = () =>
      document
        .getAnimations()
        .map(
          (animation) =>
            (animation.effect as KeyframeEffect | null)?.pseudoElement ?? "",
        )
        .filter((pseudo) => pseudo.startsWith("::view-transition-group"));
    const start = document.startViewTransition.bind(document);
    document.startViewTransition = ((options: StartViewTransitionOptions) => {
      log(`start types=${JSON.stringify(options.types ?? [])}`);
      log(`old names: ${named()}`);
      const update = options.update;
      const transition = start({
        ...options,
        update: () => {
          const done = update?.() as Promise<void> | undefined;
          log(`new names: ${named()}`);
          return done;
        },
      });
      const skip = transition.skipTransition.bind(transition);
      transition.skipTransition = () => {
        log(`skipTransition called\n${new Error("skipped by").stack ?? ""}`);
        skip();
      };
      transition.ready.then(
        () => {
          const built = groups();
          log(`ready groups: ${built.join(" ")}`);
          for (const pseudo of built) {
            if (pseudo.startsWith("::view-transition-group(morph-")) {
              record.morphsSeen?.add(pseudo);
            }
          }
        },
        (error: unknown) => log(`ready rejected ${String(error)}`),
      );
      transition.finished.then(
        () => log("finished"),
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
    const record = globalThis as {
      morphsSeen?: Set<string>;
      morphLog?: string[];
    };
    record.morphsSeen?.clear();
    record.morphLog = [`${performance.now().toFixed(0)}ms forgotten`];
  });
}

function morphLog(page: Page): Promise<string> {
  return page.evaluate(() =>
    ((globalThis as { morphLog?: string[] }).morphLog ?? []).join("\n"),
  );
}

export async function groupsDuring(
  page: Page,
  act: () => Promise<unknown>,
  expected: string[],
): Promise<void> {
  await forgetMorphs(page);
  await act();
  try {
    await expect
      .poll(() => morphsSeen(page), { timeout: 2000 })
      .toEqual(expect.arrayContaining(expected));
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new Error(`${reason}\n\nView transitions:\n${await morphLog(page)}`, {
      cause: error,
    });
  }
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
