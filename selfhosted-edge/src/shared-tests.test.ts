import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

const ROOT = join(import.meta.dirname, "../..");
const EDGE_SRC = "edge/src";
// The list of what the public repository carries, in the repository it is published from. The
// public tree has no such list, and no edge test but the ones it carries.
const OPEN_TREE = join(ROOT, ".github/copybara/copy.bara.sky");
const GLOB_PARTS: Readonly<Record<string, string>> = {
  "**": ".*",
  "*": "[^/]*",
  "?": "[^/]",
};

function globRegex(glob: string): RegExp {
  const body = glob
    .split(/(\*\*|\*|\?)/)
    .map((part) => GLOB_PARTS[part] ?? part.replace(/[.+^${}()|[\]\\]/g, "\\$&"))
    .join("");
  return new RegExp(`^${body}$`);
}

function listed(lists: string, name: string): RegExp[] {
  const items = new RegExp(`^${name} = \\[([^\\]]*)\\]`, "m").exec(lists)?.[1];
  if (items === undefined) throw new Error(`${OPEN_TREE} lists no ${name}`);
  return [...items.matchAll(/"([^"]+)"/g)].map(([, glob]) => globRegex(glob!));
}

function carriedEdgeTests(): string[] {
  const onDisk = readdirSync(join(ROOT, EDGE_SRC))
    .filter((name) => name.endsWith(".test.ts"))
    .map((name) => `${EDGE_SRC}/${name}`);
  if (!existsSync(OPEN_TREE)) return onDisk;
  const lists = readFileSync(OPEN_TREE, "utf8");
  const open = listed(lists, "OPEN_PATHS");
  const closed = listed(lists, "CLOSED_PATHS");
  return onDisk.filter(
    (path) => open.some((glob) => glob.test(path)) && !closed.some((glob) => glob.test(path)),
  );
}

describe("the shared edge modules' tests", () => {
  it("run here, every one the public repository carries", () => {
    // given
    const imports = readFileSync(join(import.meta.dirname, "shared-modules.test.ts"), "utf8");
    const imported = [...imports.matchAll(/^import "\.\.\/\.\.\/(edge\/src\/[^"]+)";$/gm)].map(
      ([, path]) => path,
    );
    const carried = carriedEdgeTests();

    // when
    const actual = carried.filter((path) => !imported.includes(path));

    // then
    expect(carried.length).toBeGreaterThan(0);
    expect(actual).toEqual([]);
  });
});
