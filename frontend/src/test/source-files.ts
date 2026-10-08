/// <reference types="node" />
import { readdirSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

import ts from "typescript";

// Not new URL(..., import.meta.url): under this repo's jsdom vitest setup, import.meta.url
// is a dev-server URL, so any relative resolution against it lands on http://localhost:3000/...
// instead of the real file. import.meta.dirname is the actual filesystem directory.
export const SRC = join(import.meta.dirname, "..");

/** The timeout for a guard that reads every shipped module. Measured on 2026-10-05, at a machine
 *  load of 25 to 90: about 0.1 to 0.6 s per guard alone, its whole cost the reads of every shipped
 *  module and the parse of those that name its token. A gate run at a load near 45 once took one
 *  sweep past 30 s when it read them one after another, so they are read side by side. */
export const TREE_WALK_TIMEOUT_MS = 30_000;

/** Every file under `dir` whose basename `keep` accepts, depth first. */
export function sourceFiles(
  dir: string,
  keep: (name: string) => boolean,
): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      found.push(...sourceFiles(path, keep));
      continue;
    }
    if (keep(entry.name)) {
      found.push(path);
    }
  }
  return found;
}

/** A TypeScript module that ships, as opposed to a test that only checks one. */
export function isShippedModule(name: string): boolean {
  return /\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name);
}

let shippedModules:
  Promise<ReadonlyArray<readonly [string, Buffer]>> | undefined;

/** Every shipped module's bytes, read side by side, and once per test file however many guards in
 *  it ask: the reads, not the parses, are most of what a guard costs. */
function readShippedModules(): Promise<
  ReadonlyArray<readonly [string, Buffer]>
> {
  shippedModules ??= Promise.all(
    sourceFiles(SRC, isShippedModule).map(
      async (path) => [path, await readFile(path)] as const,
    ),
  );
  return shippedModules;
}

/** Every shipped module whose raw text says `token`, named from `SRC`, with its comments blanked.
 *  Blanking only turns characters into spaces, so a file whose raw text never says the token cannot
 *  hold an offender, and skipping it skips its parse. */
export async function shippedSourcesSaying(
  token: string,
): Promise<Array<Readonly<{ name: string; source: string }>>> {
  return (await readShippedModules())
    .filter(([, raw]) => raw.includes(token))
    .map(([path, raw]) => ({
      name: path.slice(SRC.length + 1),
      source: withoutComments(raw.toString("utf8"), path),
    }));
}

const LITERALS: ReadonlySet<ts.SyntaxKind> = new Set([
  ts.SyntaxKind.StringLiteral,
  ts.SyntaxKind.NoSubstitutionTemplateLiteral,
  ts.SyntaxKind.TemplateHead,
  ts.SyntaxKind.TemplateMiddle,
  ts.SyntaxKind.TemplateTail,
  ts.SyntaxKind.RegularExpressionLiteral,
]);

const COMMENT_OPENER = /\/(?=[/*])/g;

/** The deepest node whose full range, leading trivia included, holds `pos`. */
function innermostAt(file: ts.SourceFile, pos: number): ts.Node {
  let holder: ts.Node = file;
  let child: ts.Node | undefined = file;
  while (child !== undefined) {
    holder = child;
    child = ts.forEachChild(holder, (node) =>
      node.pos <= pos && pos < node.end ? node : undefined,
    );
  }
  return holder;
}

/** Whether the `//` or `/*` at `pos` opens a comment. Only a literal or JSX text can hold those
 *  two characters as anything else; outside them they sit in trivia, where they open a comment. A
 *  literal's own range starts at its leading trivia, so a comment just before it still counts. */
function opensComment(file: ts.SourceFile, pos: number): boolean {
  const holder = innermostAt(file, pos);
  if (ts.isJsxText(holder)) return false;
  return !LITERALS.has(holder.kind) || pos < holder.getStart(file);
}

/** Source with its comments blanked out, line breaks kept. Guards below read markup, and a comment
 *  naming the very thing a guard forbids is prose about it, not a use of it. TypeScript's own parser
 *  places every candidate opener, so a `//` or `/*` inside a string, a template, a regular
 *  expression, JSX text or another comment is never mistaken for one. Only the openers are placed,
 *  rather than every token's trivia walked: a third of the cost. `fileName` decides whether `<T>`
 *  is JSX. */
export function withoutComments(source: string, fileName: string): string {
  const openers = [...source.matchAll(COMMENT_OPENER)].map(
    ({ index }) => index,
  );
  if (openers.length === 0) return source;
  const file = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest);
  let blanked = "";
  let cursor = 0;
  for (const pos of openers) {
    if (pos < cursor || !opensComment(file, pos)) continue;
    // Trailing, because leading ranges only start collecting after a line break.
    const end = ts.getTrailingCommentRanges(source, pos)![0]!.end;
    blanked +=
      source.slice(cursor, pos) + source.slice(pos, end).replace(/[^\n]/g, " ");
    cursor = end;
  }
  return blanked + source.slice(cursor);
}

/** Every one-line string or template literal in `source`: where class lists live. */
export function classLists(source: string): string[] {
  return [
    ...source.matchAll(/"([^"\n]*)"/g),
    ...source.matchAll(/`([^`\n]*)`/g),
  ].map((match) => match[1]!);
}
