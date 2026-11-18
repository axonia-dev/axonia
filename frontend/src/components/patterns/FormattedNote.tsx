import { Fragment, type ReactNode, useMemo } from "react";

import { cn } from "@/lib/cn";
import type {
  NoteBlock,
  NoteInline,
  NoteItem,
} from "@/lib/formatted-note/model";
import { parseNote } from "@/lib/formatted-note/parse";

interface Props {
  text: string;
  className?: string;
}

function renderInlines(inlines: NoteInline[]): ReactNode {
  return inlines.map((inline, index): ReactNode => {
    switch (inline.kind) {
      case "text":
        return <Fragment key={index}>{inline.text}</Fragment>;
      case "break":
        return <br key={index} />;
      case "strong":
        return (
          <strong key={index} className="font-semibold">
            {renderInlines(inline.children)}
          </strong>
        );
      case "emphasis":
        return <em key={index}>{renderInlines(inline.children)}</em>;
      case "link":
        return (
          <a
            key={index}
            href={inline.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary-ink underline underline-offset-4"
          >
            {renderInlines(inline.children)}
          </a>
        );
    }
  });
}

function renderPart(part: NoteBlock, index: number): ReactNode {
  return part.kind === "paragraph" ? (
    <Fragment key={index}>{renderInlines(part.inlines)}</Fragment>
  ) : (
    renderBlock(part, index)
  );
}

function renderItem(item: NoteItem, index: number): ReactNode {
  return (
    <li key={index} className="mb-1">
      {item.parts.map(renderPart)}
    </li>
  );
}

function renderBlock(block: NoteBlock, index: number): ReactNode {
  if (block.kind === "paragraph") {
    return (
      <p key={index} className="mb-2 last:mb-0">
        {renderInlines(block.inlines)}
      </p>
    );
  }
  const Tag = block.kind === "bullets" ? "ul" : "ol";
  const marker = block.kind === "bullets" ? "list-disc" : "list-decimal";
  return (
    <Tag key={index} className={cn(marker, "marker:text-primary-ink pl-5")}>
      {block.items.map(renderItem)}
    </Tag>
  );
}

/** The closing note's small markdown subset, rendered through our own model so no markup the
 *  author typed ever reaches the DOM. */
export function FormattedNote({ text, className }: Readonly<Props>) {
  const blocks = useMemo(() => parseNote(text), [text]);
  return <div className={className}>{blocks.map(renderBlock)}</div>;
}
