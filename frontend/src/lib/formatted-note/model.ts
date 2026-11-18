export type NoteInline =
  | { kind: "text"; text: string }
  | { kind: "strong" | "emphasis"; children: NoteInline[] }
  | { kind: "link"; url: string; children: NoteInline[] }
  | { kind: "break" };

/** The item's text runs and nested lists in the order written. Neighbouring paragraphs are joined
 *  by a break and neighbouring lists into one, so no two parts of one kind ever touch. */
export interface NoteItem {
  parts: NoteBlock[];
}

export type NoteBlock =
  | { kind: "paragraph"; inlines: NoteInline[] }
  | { kind: "bullets" | "numbered"; items: NoteItem[] };
