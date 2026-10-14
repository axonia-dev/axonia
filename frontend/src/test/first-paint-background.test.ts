import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { describe, expect, it } from "vitest";

import { STAGE_DARK } from "@/components/patterns/ceremony/curtain";
import {
  CEREMONY_KEY,
  CEREMONY_MAX_AGE_MS,
} from "@/features/community/api/ceremony-handoff";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

function read(path: string): string {
  return readFileSync(resolve(root, path), "utf8");
}

function darkBackground(): string | undefined {
  const theme = /\.theme-dark \{([\s\S]*?)\}/.exec(
    read("src/styles/globals.css"),
  );
  return /--background:\s*(#[0-9a-f]+);/i.exec(theme?.[1] ?? "")?.[1];
}

describe.each(["index.html", "app.html"])(
  "the first paint of %s",
  (document) => {
    it("paints the dark theme's background before the stylesheet loads", () => {
      const inline = /html\.theme-dark \{([\s\S]*?)\}/.exec(read(document));

      expect(inline?.[1]).toContain(`background: ${darkBackground()};`);
    });

    it("paints a ceremony's arrival in the stage's dark", () => {
      const inline =
        /html\.ceremony-arrival:has\(#root:empty\) body \{([\s\S]*?)\}/.exec(
          read(document),
        );

      expect(inline?.[1]).toContain(`background: ${STAGE_DARK};`);
    });

    it("reads the ceremony handoff the way the app writes it", () => {
      const html = read(document);

      expect(html).toContain(`sessionStorage.getItem("${CEREMONY_KEY}")`);
      expect(html).toContain(
        `Date.now() - ceremony.at < ${CEREMONY_MAX_AGE_MS}`,
      );
    });
  },
);
