import { useRef, useState } from "react";

import {
  filled,
  insertAt,
  newRow,
  type PatternRow,
} from "@/features/admin/domain/member-name-rows";

export function usePatternEditor(stored: ReadonlyArray<string>) {
  const [rows, setRows] = useState<PatternRow[]>(() => stored.map(newRow));
  const [focusedId, setFocusedId] = useState<number | null>(null);
  const [edits, setEdits] = useState(0);
  const inputs = useRef(new Map<number, HTMLInputElement>());

  function edit(next: PatternRow[]) {
    setEdits((count) => count + 1);
    setRows(next);
  }

  function setText(id: number, text: string) {
    edit(rows.map((row) => (row.id === id ? { ...row, text } : row)));
  }

  function insertPlaceholder(placeholder: string) {
    const target = rows.find((row) => row.id === focusedId) ?? rows.at(-1);
    if (target === undefined) {
      edit([newRow(placeholder)]);
      return;
    }
    const input = inputs.current.get(target.id);
    const start = input?.selectionStart ?? target.text.length;
    const end = input?.selectionEnd ?? start;
    setText(target.id, insertAt(target.text, placeholder, start, end));
    const caret = start + placeholder.length;
    requestAnimationFrame(() => {
      input?.focus();
      input?.setSelectionRange(caret, caret);
    });
  }

  function move(index: number, offset: -1 | 1) {
    const next = rows.slice();
    const [moved] = next.splice(index, 1);
    if (moved === undefined) return;
    next.splice(index + offset, 0, moved);
    edit(next);
  }

  function registerInput(id: number, element: HTMLInputElement | null) {
    if (element === null) inputs.current.delete(id);
    else inputs.current.set(id, element);
  }

  return {
    rows,
    patterns: filled(rows),
    edits,
    setText,
    insertPlaceholder,
    move,
    registerInput,
    focus: setFocusedId,
    add: () => edit([...rows, newRow("")]),
    remove: (id: number) => edit(rows.filter((row) => row.id !== id)),
  };
}
