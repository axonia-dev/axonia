import { readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const PACKAGE = fileURLToPath(new URL("..", import.meta.url));
const REPOSITORY = join(PACKAGE, "..");
const DOCKERFILE = readFileSync(join(PACKAGE, "Dockerfile"), "utf8");
const RUNTIME = DOCKERFILE.slice(DOCKERFILE.search(/^FROM \S+ AS runtime$/m));

const RELATIVE_IMPORT = /\bfrom\s*["'](\.\.?\/[^"']+)["']/g;

// Every module node src/main.ts loads, by its path from the repository root.
const modulesMainLoads = (): string[] => {
  const seen = new Set<string>();
  const visit = (file: string): void => {
    if (seen.has(file)) {
      return;
    }
    seen.add(file);
    for (const [, specifier] of readFileSync(file, "utf8").matchAll(RELATIVE_IMPORT)) {
      visit(resolve(dirname(file), specifier ?? ""));
    }
  };
  visit(join(PACKAGE, "src", "main.ts"));
  return [...seen].map((file) => relative(REPOSITORY, file)).sort();
};

// Where each COPY of the runtime stage puts a repository path: a directory source keeps the
// layout under it, a file lands in its destination directory under its own name.
const imagePathOf = (path: string): string | undefined => {
  for (const [, line] of RUNTIME.matchAll(/^COPY (?!--from)(.+)$/gm)) {
    const [destination, ...sources] = (line ?? "").trim().split(/\s+/).reverse();
    for (const source of sources) {
      if (source.endsWith("/") && path.startsWith(source)) {
        return join(destination ?? "", path.slice(source.length));
      }
      if (source === path) {
        return join(destination ?? "", path.split("/").at(-1) ?? "");
      }
    }
  }
  return undefined;
};

const instruction = (name: string): string[] =>
  [...RUNTIME.matchAll(new RegExp(`^${name} (.+)$`, "gm"))].map(([, value]) => value ?? "");

describe("the edge image", () => {
  it("copies every module main.ts loads to the place it is loaded from", () => {
    const misplaced = modulesMainLoads().filter(
      (path) => imagePathOf(path) !== join("/srv/edge", path),
    );

    expect(misplaced).toEqual([]);
  });

  it("serves the app from where it copies the build", () => {
    const [, built] = /^COPY --from=app \S+ (\S+)$/m.exec(RUNTIME) ?? [];

    expect(instruction("ENV")).toContain(`STATIC_DIR=${(built ?? "").replace(/\/$/, "")}`);
  });

  it("runs as a numeric user that is not root", () => {
    expect(instruction("USER")).toEqual([expect.stringMatching(/^[1-9]\d*:[1-9]\d*$/)]);
  });

  it("runs the edge with Node alone", () => {
    expect(instruction("CMD")).toEqual(['["node", "src/main.ts"]']);
    expect(RUNTIME).not.toMatch(/\b(npm|pnpm|corepack)\b/);
  });
});
