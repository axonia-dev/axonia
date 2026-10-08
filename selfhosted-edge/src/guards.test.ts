import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const PACKAGE = fileURLToPath(new URL("..", import.meta.url));
const REPOSITORY = join(PACKAGE, "..");
const SKIPPED: ReadonlySet<string> = new Set(["node_modules", "coverage", "pnpm-lock.yaml"]);
const SHARED_MODULES = ["auth.ts", "sealed-cookie.ts", "csp.ts", "edge-gate.ts"].map((name) =>
  join(REPOSITORY, "edge", "src", name),
);

const filesUnder = (directory: string): string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    if (SKIPPED.has(entry.name)) {
      return [];
    }
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesUnder(path) : [path];
  });

// Every form a module can be loaded by: "from" (import and export), a bare side-effect import, and
// import(). An import() of anything but a string literal is reported as itself: what it loads
// cannot be read here.
const IMPORT_FORMS =
  /\bfrom\s*(["'])(.*?)\1|\bimport\s*(["'])(.*?)\3|\bimport\s*\(\s*(?:(["'])(.*?)\5\s*\)|([^)]*))/g;

const specifiersIn = (source: string): string[] =>
  [...source.matchAll(IMPORT_FORMS)].map(
    (match) => match[2] ?? match[4] ?? match[6] ?? `import(${match[7] ?? ""})`,
  );

const SOURCE_ROOT = join(PACKAGE, "src");

// Node's own modules, this package's sources, and the four shared modules, which may import each
// other; nothing else, a relative path out to another package of the repository included. node:module
// is the one Node module refused: its createRequire loads a package past every form read above.
const isAdmitted = (file: string, specifier: string): boolean => {
  if (specifier.startsWith("node:")) {
    return specifier !== "node:module";
  }
  if (!/^\.\.?\//.test(specifier)) {
    return false;
  }
  const target = resolve(dirname(file), specifier);
  return target.startsWith(`${SOURCE_ROOT}${sep}`) || SHARED_MODULES.includes(target);
};

const offendingImports = (file: string, source: string): string[] =>
  specifiersIn(source).filter((specifier) => !isAdmitted(file, specifier));

describe("the package", () => {
  it("writes no en dash or em dash anywhere", () => {
    const offenders = filesUnder(PACKAGE).filter((file) =>
      /[\u2013\u2014]/.test(readFileSync(file, "utf8")),
    );

    expect(offenders.map((file) => relative(PACKAGE, file))).toEqual([]);
  });

  // The image installs nothing: the code it runs, this package's and the four shared
  // modules', may import Node's own modules and each other, and no package from npm.
  it("runs on Node alone", () => {
    const runtime = [
      ...filesUnder(join(PACKAGE, "src")).filter(
        (file) => !file.endsWith(".test.ts") && !file.endsWith("test-support.ts"),
      ),
      ...SHARED_MODULES,
    ];
    const offenders = runtime.flatMap((file) =>
      offendingImports(file, readFileSync(file, "utf8")).map(
        (specifier) => `${relative(REPOSITORY, file)}: ${specifier}`,
      ),
    );

    expect(offenders).toEqual([]);
  });

  const SOURCE = join(SOURCE_ROOT, "a-module.ts");

  it.each([
    ['import { x } from "a-package";', "a-package"],
    ['export { x } from "a-package";', "a-package"],
    ['import "a-package";', "a-package"],
    ['const x = await import("a-package");', "a-package"],
    ["const x = await import('a-package');", "a-package"],
    ["const x = await import(name);", "import(name)"],
    ['import { csp } from "../../other-edge/src/csp.ts";', "../../other-edge/src/csp.ts"],
    ['import { x } from "../../edge/src/index.ts";', "../../edge/src/index.ts"],
    ['import "../package.json";', "../package.json"],
    ['import { createRequire } from "node:module";', "node:module"],
  ])("would catch %s", (source, specifier) => {
    expect(offendingImports(SOURCE, source)).toEqual([specifier]);
  });

  it.each([
    'import { x } from "node:http";',
    'const x = await import("node:fs");',
    'import { x } from "./config.ts";',
    'export { x } from "../../edge/src/auth.ts";',
    'import { readFile } from "node:fs/promises";',
  ])("would admit %s", (source) => {
    expect(offendingImports(SOURCE, source)).toEqual([]);
  });
});

describe("the Self-hosted document", () => {
  // Its policy names no script hash (config.ts), so an inline script would be blocked in every
  // browser, silently: the page would load and do nothing.
  it("carries no inline script", () => {
    const document = readFileSync(join(REPOSITORY, "frontend", "selfhosted.html"), "utf8");

    expect(document.match(/<script(?![^>]*\bsrc=)[^>]*>/g)).toBeNull();
  });
});
