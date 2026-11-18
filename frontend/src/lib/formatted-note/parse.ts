import type {
  Link,
  List,
  ListItem,
  Nodes,
  PhrasingContent,
  RootContent,
} from "mdast";
import { fromMarkdown } from "mdast-util-from-markdown";
import { gfmAutolinkLiteralFromMarkdown } from "mdast-util-gfm-autolink-literal";
import { gfmAutolinkLiteral } from "micromark-extension-gfm-autolink-literal";

import { foundText, withFoundUrls } from "./autolinks";
import type { NoteBlock, NoteInline, NoteItem } from "./model";
import { isTooDeep, plainBlocks } from "./nesting";

const LITERAL_CONSTRUCTS = [
  "headingAtx",
  "setextUnderline",
  "thematicBreak",
  "blockQuote",
  "codeFenced",
  "codeIndented",
  "htmlFlow",
  "htmlText",
  "definition",
];

const MAX_NESTING = 2;
const FIRST_ITEM_DELIMITER = /^(\d+)([.)])/;
const RULE = /^([-*_])\1{2,}$/;

function sourceOf(node: Nodes, source: string): string {
  const start = node.position?.start.offset ?? 0;
  const end = node.position?.end.offset ?? 0;
  return source.slice(start, end);
}

function textWithBreaks(value: string): NoteInline[] {
  return value.split("\n").flatMap((line, index): NoteInline[] => {
    const text: NoteInline[] =
      line === "" ? [] : [{ kind: "text", text: line }];
    return index === 0 ? text : [{ kind: "break" }, ...text];
  });
}

function hasText(inlines: NoteInline[]): boolean {
  return inlines.some((inline) => {
    switch (inline.kind) {
      case "text":
        return !/^[ \t\n]*$/.test(inline.text);
      case "break":
        return false;
      default:
        return hasText(inline.children);
    }
  });
}

function isSafeLink(url: string, written: string): boolean {
  return /^https?:\/\//i.test(url) && !/^www\./i.test(written);
}

function linkOf(node: Link, source: string): NoteInline[] {
  const children = inlinesOf(node.children, source);
  const written = node.position ? sourceOf(node, source) : foundText(node);
  return isSafeLink(node.url, written) && hasText(children)
    ? [{ kind: "link", url: node.url, children }]
    : children;
}

function inlineOf(node: PhrasingContent, source: string): NoteInline[] {
  switch (node.type) {
    case "text":
      return textWithBreaks(node.value);
    case "break":
      return [{ kind: "break" }];
    case "strong":
      return [{ kind: "strong", children: inlinesOf(node.children, source) }];
    case "emphasis":
      return [{ kind: "emphasis", children: inlinesOf(node.children, source) }];
    case "link":
      return linkOf(node, source);
    case "image":
    case "inlineCode":
      return textWithBreaks(sourceOf(node, source));
    default:
      return "children" in node ? inlinesOf(node.children, source) : [];
  }
}

function inlinesOf(nodes: PhrasingContent[], source: string): NoteInline[] {
  return withFoundUrls(nodes).flatMap((node) => inlineOf(node, source));
}

function listBlocks(list: List, source: string): NoteBlock[] {
  if (list.ordered && list.start !== 1) {
    const escaped = sourceOf(list, source).replace(
      FIRST_ITEM_DELIMITER,
      "$1\\$2",
    );
    return blocksOf(escaped);
  }
  return [
    {
      kind: list.ordered ? "numbered" : "bullets",
      items: list.children.map((item) => itemOf(item, source)),
    },
  ];
}

function appendPart(parts: NoteBlock[], block: NoteBlock): NoteBlock[] {
  const last = parts.at(-1);
  if (last?.kind === "paragraph" && block.kind === "paragraph") {
    const inlines: NoteInline[] = [
      ...last.inlines,
      { kind: "break" },
      ...block.inlines,
    ];
    return [...parts.slice(0, -1), { kind: "paragraph", inlines }];
  }
  if (last && last.kind !== "paragraph" && block.kind !== "paragraph") {
    const items = [...last.items, ...block.items];
    return [...parts.slice(0, -1), { kind: last.kind, items }];
  }
  return [...parts, block];
}

function itemOf(item: ListItem, source: string): NoteItem {
  const parts = item.children
    .flatMap((child) => blockOf(child, source))
    .reduce(appendPart, []);
  return { parts };
}

function blockOf(node: RootContent, source: string): NoteBlock[] {
  if (node.type === "paragraph") {
    return [{ kind: "paragraph", inlines: inlinesOf(node.children, source) }];
  }
  return node.type === "list" ? listBlocks(node, source) : [];
}

function flattened(item: NoteItem): NoteItem[] {
  const [first] = item.parts;
  const lead: NoteItem[] =
    !first || first.kind !== "paragraph" ? [{ parts: [] }] : [];
  return [
    ...lead,
    ...item.parts.flatMap((part) =>
      part.kind === "paragraph"
        ? [{ parts: [part] }]
        : part.items.flatMap(flattened),
    ),
  ];
}

function limitDepth(block: NoteBlock, level: number): NoteBlock {
  if (block.kind === "paragraph") {
    return block;
  }
  const items = block.items.flatMap((item): NoteItem[] =>
    level < MAX_NESTING
      ? [{ parts: item.parts.map((part) => limitDepth(part, level + 1)) }]
      : flattened(item),
  );
  return { ...block, items };
}

function blocksOf(markdown: string): NoteBlock[] {
  const tree = fromMarkdown(markdown, {
    extensions: [
      gfmAutolinkLiteral(),
      { disable: { null: LITERAL_CONSTRUCTS } },
    ],
    mdastExtensions: [gfmAutolinkLiteralFromMarkdown()],
  });
  return tree.children.flatMap((node) => blockOf(node, markdown));
}

function isVisible(block: NoteBlock): boolean {
  return block.kind !== "paragraph" || hasText(block.inlines);
}

/** A line of three or more of one rule mark is no rule here but the text typed. */
function ruleAsText(line: string): string {
  if (!RULE.test(line.replace(/[ \t]/g, ""))) {
    return line;
  }
  const start = line.search(/[^ \t]/);
  return `${line.slice(0, start)}\\${line.slice(start)}`;
}

function parsedBlocks(markdown: string): NoteBlock[] {
  if (isTooDeep(markdown)) {
    return plainBlocks(markdown);
  }
  const rulesAsText = markdown.split("\n").map(ruleAsText).join("\n");
  return blocksOf(rulesAsText).map((block) => limitDepth(block, 1));
}

export function parseNote(markdown: string | null): NoteBlock[] {
  if (!markdown) {
    return [];
  }
  return parsedBlocks(markdown.replace(/\r\n?/g, "\n")).filter(isVisible);
}
