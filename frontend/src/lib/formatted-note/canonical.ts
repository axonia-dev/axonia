import type { NoteBlock, NoteInline, NoteItem } from "./model";

const ESCAPES = new Map([
  ["&", "&amp;"],
  ["<", "&lt;"],
  [">", "&gt;"],
  ['"', "&quot;"],
]);

function escapeHtml(value: string): string {
  return value.replace(/[&<>"]/g, (char) => ESCAPES.get(char) ?? char);
}

function inlineHtml(inline: NoteInline): string {
  switch (inline.kind) {
    case "text":
      return escapeHtml(inline.text);
    case "break":
      return "<br>";
    case "strong":
      return `<strong>${inlinesHtml(inline.children)}</strong>`;
    case "emphasis":
      return `<em>${inlinesHtml(inline.children)}</em>`;
    case "link":
      return `<a href="${escapeHtml(inline.url)}">${inlinesHtml(inline.children)}</a>`;
  }
}

function inlinesHtml(inlines: NoteInline[]): string {
  return inlines.map(inlineHtml).join("");
}

function partHtml(part: NoteBlock): string {
  return part.kind === "paragraph"
    ? inlinesHtml(part.inlines)
    : blockHtml(part);
}

function itemHtml(item: NoteItem): string {
  return `<li>${item.parts.map(partHtml).join("")}</li>`;
}

function blockHtml(block: NoteBlock): string {
  if (block.kind === "paragraph") {
    return `<p>${inlinesHtml(block.inlines)}</p>`;
  }
  const tag = block.kind === "bullets" ? "ul" : "ol";
  return `<${tag}>${block.items.map(itemHtml).join("")}</${tag}>`;
}

export function toCanonicalHtml(blocks: NoteBlock[]): string {
  return blocks.map(blockHtml).join("");
}
