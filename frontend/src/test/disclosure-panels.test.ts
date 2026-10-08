import ts from "typescript";
import { describe, expect, it } from "vitest";

import {
  shippedSourcesSaying,
  TREE_WALK_TIMEOUT_MS,
} from "@/test/source-files";

// A shut DisclosurePanel is `hidden="until-found"`, which the browser draws as
// `content-visibility: hidden`, not `display: none`, and Tailwind's preflight exempts it from its
// own `[hidden]` rule. The panel's box stays on screen as a zero-height item, so padding, a margin,
// a border, a ring, a shadow or a height on it shows under the trigger of every shut section, and
// so does a gap its Disclosure puts before it. Spacing belongs on an element inside the panel.
// jsdom has no layout, so no other gate can see it.
const SPACING = /^-?[mp][bltrxyse]?-/;
const BORDER_WIDTH =
  /^border(?:-[bltrxyse])?$|^border-(?:[bltrxyse]-)?(?:\d+|\[[^\]]*\])$/;
const OUTLINE_OR_RING =
  /^(?:ring|outline)$|^(?:ring|outline)-(?:\d+|\[[^\]]*\])$/;
const SHADOW = /^shadow$|^shadow-(?:2xs|xs|sm|md|lg|xl|2xl|inner|\[[^\]]*\])$/;
const HEIGHT = /^(?:min-)?(?:h|size)-/;
const GAP_BEFORE_THE_PANEL = /^(?:gap-(?!x-)|space-y-)/;

type Rule = (utility: string) => boolean;

const drawsABox: Rule = (utility) =>
  [SPACING, BORDER_WIDTH, OUTLINE_OR_RING, SHADOW, HEIGHT].some((rule) =>
    rule.test(utility),
  );

const spacesItsItems: Rule = (utility) => GAP_BEFORE_THE_PANEL.test(utility);

/** The utility a class names, at any breakpoint or state, without its important mark. */
function utilityOf(token: string): string {
  return token.slice(token.lastIndexOf(":") + 1).replace(/^!|!$/g, "");
}

/** Every variable in the file by name, so a class list held in a constant is read too. */
function constantsOf(file: ts.SourceFile): Map<string, ts.Node> {
  const constants = new Map<string, ts.Node>();
  const visit = (node: ts.Node): void => {
    if (
      ts.isVariableDeclaration(node) &&
      ts.isIdentifier(node.name) &&
      node.initializer !== undefined
    ) {
      constants.set(node.name.text, node.initializer);
    }
    node.forEachChild(visit);
  };
  visit(file);
  return constants;
}

/** The text of every string under `node`, following the file's own constants it names. */
function stringsUnder(
  node: ts.Node,
  constants: Map<string, ts.Node>,
): string[] {
  const found: string[] = [];
  const followed = new Set<string>();
  const follow = (name: string): void => {
    const constant = constants.get(name);
    if (constant === undefined || followed.has(name)) return;
    followed.add(name);
    visit(constant);
  };
  const visit = (at: ts.Node): void => {
    if (ts.isStringLiteralLike(at) || ts.isTemplateLiteralToken(at)) {
      found.push(at.text);
    }
    if (ts.isIdentifier(at)) follow(at.text);
    at.forEachChild(visit);
  };
  visit(node);
  return found;
}

function tagsNamed(
  file: ts.SourceFile,
  name: string,
): ts.JsxOpeningLikeElement[] {
  const tags: ts.JsxOpeningLikeElement[] = [];
  const visit = (node: ts.Node): void => {
    if (
      (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) &&
      node.tagName.getText(file) === name
    ) {
      tags.push(node);
    }
    node.forEachChild(visit);
  };
  visit(file);
  return tags;
}

function classTokens(
  tag: ts.JsxOpeningLikeElement,
  constants: Map<string, ts.Node>,
): string[] {
  const className = tag.attributes.properties.find(
    (attribute) =>
      ts.isJsxAttribute(attribute) &&
      ts.isIdentifier(attribute.name) &&
      attribute.name.text === "className",
  );
  if (className === undefined) return [];
  return stringsUnder(className, constants).flatMap((text) =>
    text.split(/\s+/).filter((token) => token !== ""),
  );
}

interface Sweep {
  elements: number;
  offenders: string[];
}

/** Every class on a `tag` element of the shipped sources that `rule` refuses. */
async function sweep(tag: string, rule: Rule): Promise<Sweep> {
  const found: Sweep = { elements: 0, offenders: [] };
  for (const { name, source } of await shippedSourcesSaying(`<${tag}`)) {
    const file = ts.createSourceFile(
      name,
      source,
      ts.ScriptTarget.Latest,
      true,
    );
    const constants = constantsOf(file);
    for (const element of tagsNamed(file, tag)) {
      found.elements += 1;
      for (const token of classTokens(element, constants)) {
        if (rule(utilityOf(token))) found.offenders.push(`${name}: ${token}`);
      }
    }
  }
  return found;
}

describe("disclosure panels", () => {
  it(
    "carry no spacing of their own, which a shut panel would still draw",
    async () => {
      const { elements, offenders } = await sweep("DisclosurePanel", drawsABox);
      expect(elements).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );

  it(
    "sit in a disclosure that puts no gap before them",
    async () => {
      const { elements, offenders } = await sweep("Disclosure", spacesItsItems);
      expect(elements).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
