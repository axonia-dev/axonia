/// <reference types="node" />
import { readFileSync } from "node:fs";
import { join } from "node:path";

import {
  isShippedModule,
  sourceFiles,
  SRC,
  withoutComments,
} from "@/test/source-files";

export interface Allowed {
  file: string;
  text: string;
}

export interface VoiceScope {
  /** The directory to walk, relative to `src`. */
  root: string;
  /** Directories left out, relative to `src`, each matched as a prefix. */
  skip: ReadonlyArray<string>;
  allowed: ReadonlyArray<Allowed>;
}

const PRONOUN =
  /\b(you|your|yours|yourself|yourselves|we|us|our|ours|ourselves)\b/i;

// MDX is prose and is scanned too; the shared walker's default keeps .ts/.tsx only.
function isCopyFile(name: string): boolean {
  return /\.mdx$/.test(name) || isShippedModule(name);
}

// Cosmetic only: the capturing regex below already excludes `<`/`>` from inside a `{...}`
// span, so a match can never straddle a nested element. This just blanks the interpolation
// out of the text a human reads in the offender list.
function stripInterpolations(text: string): string {
  return text.replace(/\{[^{}]*\}/g, " ");
}

// Double-quoted strings, template chunks and JSX text. Comments are not copy, and this
// codebase's JSDoc uses "you" to address a developer, which is fine.
//
// The JSX pattern crosses a *simple* `{...}` span (no nested braces, no `<`/`>` inside it)
// so `>Your {name} is ready<` reads as one run instead of vanishing between two delimiters
// neither of which is directly followed by the other. A run may also open on such spans, so
// text after an interpolation or a JSX comment (`>{count} of your items<`) is read too, where
// a run that had to open on a letter skipped it. Interpolation stripping has to happen
// on the already-captured, already-bounded run, not on the raw file: a bare `>` still ends
// the match exactly as before (from an arrow function, a comparison, a generic), which is
// what keeps this from running away. An earlier version of this pattern allowed any
// character (including `<`/`>`) inside the run to fix the interpolation gap, and instead
// matched from an arrow function's `=>` clear across an unrelated file to a later generic's
// `<T>`, capturing everything in between as one "JSX text" node.
const JSX_TEXT =
  // eslint-disable-next-line security/detect-unsafe-regex -- stress-tested linear: the alternation branches, and the opening spans and the letter after them, are disjoint on their first character
  />\s*((?:\{[^{}<>]*\}\s*)*[A-Za-z](?:[^<>{}]|\{[^{}<>]*\})*)</g;

function copyStrings(file: string, source: string): string[] {
  const code = withoutComments(source, file);
  const matches = [
    ...code.matchAll(/"([^"\\\n]+)"/g),
    ...code.matchAll(/`([^`]+)`/g),
  ].map((match) => match[1]!);
  const jsxMatches = [...code.matchAll(JSX_TEXT)].map((match) =>
    stripInterpolations(match[1]!),
  );
  return [...matches, ...jsxMatches];
}

// MDX is prose, not JS: no quoted-string, template-literal or JSX-text parsing applies.
// Every non-blank, non-import line is copy, scanned whole rather than extracted piecemeal.
function mdxProseStrings(source: string): string[] {
  return source
    .split("\n")
    .filter((line) => line.trim() !== "" && !line.startsWith("import "));
}

function stringsIn(file: string, source: string): string[] {
  return file.endsWith(".mdx")
    ? mdxProseStrings(source)
    : copyStrings(file, source);
}

/** Every copy string under the scope that addresses the reader as you or speaks as we. */
export function voiceOffenders(scope: VoiceScope): string[] {
  const offenders: string[] = [];
  for (const path of sourceFiles(join(SRC, scope.root), isCopyFile)) {
    const file = path.slice(SRC.length + 1);
    if (scope.skip.some((dir) => file.startsWith(dir))) continue;
    const source = readFileSync(path, "utf8");
    // Every string read below is the source with comments and interpolations blanked to spaces, which adds no
    // letter, so a file whose raw text holds no pronoun cannot offend. Skipping it skips its TypeScript parse,
    // which is nearly the whole cost of this guard, for all but a few dozen of a thousand files.
    if (!PRONOUN.test(source)) continue;
    for (const text of stringsIn(file, source)) {
      const trimmed = text.trim();
      if (!PRONOUN.test(text)) continue;
      if (
        scope.allowed.some(
          (entry) => entry.file === file && entry.text === trimmed,
        )
      )
        continue;
      offenders.push(`${file}: ${trimmed}`);
    }
  }
  return offenders;
}
